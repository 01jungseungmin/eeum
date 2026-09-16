package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.RejectRequestDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.application.store.service.StoreLocationResolver;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사장 심사 상태 전이 회귀 테스트.
 *
 * <p>고정하는 계약은 세 가지다.
 * <ul>
 *   <li>승인·거절은 <b>심사 대기 중(PENDING + 접수 시각)</b>인 신청만 대상으로 한다. 상태만 보면
 *       미제출 신청이 승인되고, 접수 시각만 보면 거절된 신청이 재신청 없이 승인된다.</li>
 *   <li>거절도 승인과 같은 잠금 규약(accountId projection → Account 잠금 → OwnerInfo 잠금 →
 *       재검증)을 <b>같은 순서로</b> 쓴다. 순서가 갈리면 교착이 나고, 잠그지 않으면 먼저 커밋된
 *       승인을 못 보고 ROLE_OWNER + REJECTED가 남는다.</li>
 *   <li>승인은 계정 상태를 {@code assertWritable()}로 판정해 탈퇴·정지·가입 미완료를 구분해
 *       응답한다. 거절은 계정 상태를 보지 않는다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountServiceOwnerReviewGuardTest {

    @InjectMocks AdminAccountService adminAccountService;

    @Mock AccountRepository accountRepository;
    @Mock AccountRegionRepository accountRegionRepository;
    @Mock OwnerInfoRepository ownerInfoRepository;
    @Mock StoreRepository storeRepository;
    @Mock StoreLocationResolver storeLocationResolver;
    @Mock StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    @Mock AccountMapper accountMapper;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock StoreApprovalMapper storeApprovalMapper;
    @Mock AccountWithdrawalProcessor accountWithdrawalProcessor;
    @Mock AccountSanctionPolicy accountSanctionPolicy;
    @Mock SanctionHistoryService sanctionHistoryService;
    @Mock ApplicationEventPublisher eventPublisher;

    private static final Long OWNER_INFO_ID = 10L;
    private static final Long ACCOUNT_ID = 3L;

    // 계정 상태 판정은 Mock으로 두면 assertWritable()이 무력화된다 — 실제 엔티티를 쓴다.
    private Account account(AccountStatus status) {
        Account account = Account.createUser(
                "owner@test.com", "encoded-pw", "김사장", "사장닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }

    // OwnerInfo도 Mock으로 두면 isAwaitingReview()가 무력화돼 "미접수"와 "거절"이 구분되지 않는다.
    // 실제 엔티티에 실제 전이를 적용해 판별식이 계산되게 한다.
    private OwnerInfo locked(Account account, Transition transition) {
        OwnerInfo ownerInfo = OwnerInfo.create(account, "123-45-67890", LocalDate.of(2020, 1, 1));
        ReflectionTestUtils.setField(ownerInfo, "ownerInfoId", OWNER_INFO_ID);
        transition.applyTo(ownerInfo);

        when(ownerInfoRepository.findAccountIdByOwnerInfoId(OWNER_INFO_ID))
                .thenReturn(Optional.of(ACCOUNT_ID));
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(ownerInfo));
        return ownerInfo;
    }

    private enum Transition {
        NOT_SUBMITTED,      // create 직후 — PENDING + 접수 시각 없음
        AWAITING_REVIEW,    // 심사 요청 완료 — PENDING + 접수 시각
        REJECTED,           // 거절 — REJECTED + 접수 시각은 남음
        APPROVED;

        void applyTo(OwnerInfo ownerInfo) {
            switch (this) {
                case NOT_SUBMITTED -> { }
                case AWAITING_REVIEW -> ownerInfo.requestReview();
                case REJECTED -> {
                    ownerInfo.requestReview();
                    ownerInfo.reject("최초 거절 사유");
                }
                case APPROVED -> {
                    ownerInfo.requestReview();
                    ownerInfo.approve();
                }
            }
        }
    }

    // ─────────────────── approveOwner 선행조건 ───────────────────

    @Test
    void approveOwner_접수되지_않은_신청은_승인할_수_없다() {
        // given: PENDING이지만 아직 심사 요청을 넣지 않은 신청 (create 직후 · 사업자번호 변경 직후)
        OwnerInfo ownerInfo = locked(account(AccountStatus.ACTIVE), Transition.NOT_SUBMITTED);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_NOT_PENDING);

        assertThat(ownerInfo.getApprovalStatus()).isNotEqualTo(ApprovalStatus.APPROVED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void approveOwner_거절된_신청은_재신청_없이_승인할_수_없다() {
        // given: reject()는 상태만 REJECTED로 바꾸고 접수 시각을 남긴다.
        // 접수 시각만 보고 판정하면 거절된 신청이 그대로 ROLE_OWNER가 된다.
        Account account = account(AccountStatus.ACTIVE);
        OwnerInfo ownerInfo = locked(account, Transition.REJECTED);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_NOT_PENDING);

        assertThat(ownerInfo.getApprovalStatus()).isNotEqualTo(ApprovalStatus.APPROVED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void approveOwner_탈퇴한_계정에는_사장_권한을_주지_않는다() {
        // given: 승인은 ROLE_OWNER 부여다 — 죽은 계정에 권한이 붙으면 탈퇴가 무력화된다
        OwnerInfo ownerInfo = locked(account(AccountStatus.WITHDRAWN), Transition.AWAITING_REVIEW);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        assertThat(ownerInfo.getAccount().getRole()).isEqualTo(AccountRole.ROLE_USER);
    }

    @Test
    void approveOwner_정지된_계정에는_사장_권한을_주지_않는다() {
        // given
        OwnerInfo ownerInfo = locked(account(AccountStatus.SUSPENDED), Transition.AWAITING_REVIEW);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        assertThat(ownerInfo.getAccount().getRole()).isEqualTo(AccountRole.ROLE_USER);
    }

    @Test
    void approveOwner_가입_미완료_계정은_정지가_아니라_가입_미완료로_거절한다() {
        // given: 상태를 한 덩어리로 묶으면 공개 오류 계약이 어긋난다
        OwnerInfo ownerInfo = locked(account(AccountStatus.PENDING), Transition.AWAITING_REVIEW);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);

        assertThat(ownerInfo.getAccount().getRole()).isEqualTo(AccountRole.ROLE_USER);
    }

    @Test
    void approveOwner_이미_승인된_신청은_계정_상태보다_먼저_차단된다() {
        // given: 이미 처리된 신청이라는 사실이 계정 상태보다 정확한 응답이다
        locked(account(AccountStatus.SUSPENDED), Transition.APPROVED);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);
    }

    // ─────────────────── rejectOwner 잠금 규약 ───────────────────

    @Test
    void rejectOwner_승인과_같은_잠금_순서를_쓴다() {
        // given: 잠금 없이 findById로 읽으면 먼저 커밋된 승인을 보지 못한 채 상태만 REJECTED로
        // 덮어 ROLE_OWNER + REJECTED가 남는다. 순서가 승인과 갈리면 교착이 난다.
        OwnerInfo ownerInfo = locked(account(AccountStatus.ACTIVE), Transition.AWAITING_REVIEW);

        RejectRequestDto request = mock(RejectRequestDto.class);
        when(request.getReason()).thenReturn("요건 미충족");

        // when
        adminAccountService.rejectOwner(0L, OWNER_INFO_ID, request);

        // then: accountId projection → Account 잠금 → OwnerInfo 잠금 순서로 고정
        InOrder inOrder = inOrder(ownerInfoRepository, accountRepository);
        inOrder.verify(ownerInfoRepository).findAccountIdByOwnerInfoId(OWNER_INFO_ID);
        inOrder.verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        inOrder.verify(ownerInfoRepository).findByAccountIdWithLock(ACCOUNT_ID);

        // 잠금 없는 조회로 되돌아가지 않았는지 고정
        verify(ownerInfoRepository, never()).findById(anyLong());
        assertThat(ownerInfo.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(ownerInfo.getRejectionReason()).isEqualTo("요건 미충족");
    }

    @Test
    void rejectOwner_접수되지_않은_신청은_거절할_수_없다() {
        // given
        OwnerInfo ownerInfo = locked(account(AccountStatus.ACTIVE), Transition.NOT_SUBMITTED);

        // when & then
        assertThatThrownBy(() -> adminAccountService.rejectOwner(
                0L, OWNER_INFO_ID, mock(RejectRequestDto.class)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_NOT_PENDING);

        assertThat(ownerInfo.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(ownerInfo.getRejectionReason()).isNull();
    }

    @Test
    void rejectOwner_이미_거절된_신청은_다시_거절할_수_없다() {
        // given: 재거절을 허용하면 최초 거절 사유가 덮인다
        OwnerInfo ownerInfo = locked(account(AccountStatus.ACTIVE), Transition.REJECTED);

        RejectRequestDto request = mock(RejectRequestDto.class);

        // when & then
        assertThatThrownBy(() -> adminAccountService.rejectOwner(0L, OWNER_INFO_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_NOT_PENDING);

        // 최초 거절 사유가 그대로 남는다
        assertThat(ownerInfo.getRejectionReason()).isEqualTo("최초 거절 사유");
    }

    @Test
    void rejectOwner_탈퇴한_신청자도_대기열에서_거절할_수_있다() {
        // given: 승인과 달리 거절은 아무 권한도 주지 않는다 — 죽은 계정의 신청을 정리할 수 있어야 한다
        OwnerInfo ownerInfo = locked(account(AccountStatus.WITHDRAWN), Transition.AWAITING_REVIEW);

        RejectRequestDto request = mock(RejectRequestDto.class);
        when(request.getReason()).thenReturn("탈퇴 계정");

        // when
        adminAccountService.rejectOwner(0L, OWNER_INFO_ID, request);

        // then: 계정 상태를 이유로 막지 않는다
        assertThat(ownerInfo.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(ownerInfo.getRejectionReason()).isEqualTo("탈퇴 계정");
    }
}
