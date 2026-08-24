package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.RejectRequestDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.application.store.service.StoreLocationResolver;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사장 심사 상태 전이 회귀 테스트.
 *
 * <p>고정하는 계약은 두 가지다.
 * <ul>
 *   <li>승인·거절은 "접수 완료된 미승인 신청"만 대상으로 한다. PENDING은 미제출 상태도 겸하므로
 *       상태만 보면 체크리스트도 안 끝낸 신청이 ownerInfoId만으로 승인된다.</li>
 *   <li>거절도 승인과 같은 잠금 규약(account → owner_info 잠금 후 재검증)을 쓴다. 잠그지 않으면
 *       먼저 커밋된 승인을 못 보고 ROLE_OWNER + REJECTED가 남는다.</li>
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

    private OwnerInfo lockedOwnerInfo(Account account) {
        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfoRepository.findAccountIdByOwnerInfoId(OWNER_INFO_ID))
                .thenReturn(Optional.of(ACCOUNT_ID));
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(ownerInfoRepository.findByAccountIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(ownerInfo));
        return ownerInfo;
    }

    // ─────────────────── approveOwner 선행조건 ───────────────────

    @Test
    void approveOwner_접수되지_않은_신청은_승인할_수_없다() {
        // given: PENDING이지만 아직 심사 요청을 넣지 않은 신청 (create 직후 · 사업자번호 변경 직후)
        Account account = mock(Account.class);
        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isReviewRequested()).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_NOT_REQUESTED);

        verify(ownerInfo, never()).approve();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void approveOwner_탈퇴한_계정에는_사장_권한을_주지_않는다() {
        // given: 승인은 ROLE_OWNER 부여다 — 죽은 계정에 권한이 붙으면 탈퇴가 무력화된다
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(true);

        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isReviewRequested()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(ownerInfo, never()).approve();
    }

    @Test
    void approveOwner_정지된_계정에는_사장_권한을_주지_않는다() {
        // given
        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isSuspended()).thenReturn(true);

        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isReviewRequested()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(ownerInfo, never()).approve();
    }

    // ─────────────────── rejectOwner 잠금 규약 ───────────────────

    @Test
    void rejectOwner_잠금_조회로_읽는다_잠금_없는_조회_금지() {
        // given: 잠금 없이 findById로 읽으면 먼저 커밋된 승인을 보지 못한 채
        // 상태만 REJECTED로 덮어 ROLE_OWNER + REJECTED가 남는다.
        Account account = mock(Account.class);
        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isReviewRequested()).thenReturn(true);

        RejectRequestDto request = mock(RejectRequestDto.class);
        when(request.getReason()).thenReturn("요건 미충족");

        // when
        adminAccountService.rejectOwner(0L, OWNER_INFO_ID, request);

        // then
        verify(ownerInfoRepository).findByAccountIdWithLock(ACCOUNT_ID);
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        verify(ownerInfoRepository, never()).findById(anyLong());
        verify(ownerInfo).reject("요건 미충족");
    }

    @Test
    void rejectOwner_접수되지_않은_신청은_거절할_수_없다() {
        // given
        Account account = mock(Account.class);
        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isReviewRequested()).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> adminAccountService.rejectOwner(
                0L, OWNER_INFO_ID, mock(RejectRequestDto.class)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_NOT_REQUESTED);

        verify(ownerInfo, never()).reject(any());
    }

    @Test
    void rejectOwner_탈퇴한_신청자도_대기열에서_거절할_수_있다() {
        // given: 승인과 달리 거절은 아무 권한도 주지 않는다 — 죽은 계정의 신청을 정리할 수 있어야 한다
        Account account = mock(Account.class);
        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isReviewRequested()).thenReturn(true);

        RejectRequestDto request = mock(RejectRequestDto.class);
        when(request.getReason()).thenReturn("탈퇴 계정");

        // when
        adminAccountService.rejectOwner(0L, OWNER_INFO_ID, request);

        // then: 계정 상태를 이유로 막지 않는다
        verify(ownerInfo).reject("탈퇴 계정");
        verify(account, never()).isWithdrawn();
    }

    // 상태 검증이 계정 검증보다 앞선다 — 이미 처리된 신청이라는 사실이 더 정확한 응답이다.
    @Test
    void approveOwner_이미_승인된_신청은_계정_상태보다_먼저_차단된다() {
        // given
        Account account = mock(Account.class);
        OwnerInfo ownerInfo = lockedOwnerInfo(account);
        when(ownerInfo.isApproved()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> adminAccountService.approveOwner(0L, OWNER_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);

        verify(account, never()).isActive();
    }
}
