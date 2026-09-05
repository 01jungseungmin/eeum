package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.event.OwnerApplicationSubmittedEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
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
import java.time.LocalDateTime;
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
 * 입점 심사 재신청 회귀 테스트.
 *
 * <p>고정하는 계약은 세 가지다.
 * <ul>
 *   <li>심사 대기 중(PENDING + 접수 시각)인 신청은 다시 접수되지 않는다. 재접수를 허용하면
 *       신청 시각이 갱신돼 대기열 순서가 밀리고 관리자 알림이 요청 횟수만큼 재발행된다.
 *       반대로 REJECTED는 재신청 대상이므로 막지 않는다 — 승인·거절과 정확히 반대 조건이다.</li>
 *   <li>OwnerInfo는 잠금 조회로, 승인/거절과 <b>같은 순서</b>(account → owner_info)로 읽는다.
 *       잠그지 않으면 관리자 승인이 먼저 커밋된 뒤 이 트랜잭션이 상태를 PENDING으로 되돌려
 *       ROLE_OWNER + PENDING이 남는다(@Version 없음 → 행 전체 덮어쓰기).</li>
 *   <li>계정 상태는 탈퇴·정지·가입 미완료를 구분해 응답한다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OwnerApprovalServiceRequestReviewTest {

    @InjectMocks OwnerApprovalService ownerApprovalService;

    @Mock AccountRepository accountRepository;
    @Mock OwnerInfoRepository ownerInfoRepository;
    @Mock StoreRepository storeRepository;
    @Mock SettlementAccountRepository settlementAccountRepository;
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductCategoryRepository productCategoryRepository;
    @Mock StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock StoreApprovalMapper storeApprovalMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    private static final Long ACCOUNT_ID = 1L;
    private static final Long STORE_ID = 100L;

    // 계정 상태 판정은 Mock으로 두면 assertWritable()이 무력화된다 — 실제 엔티티를 쓴다.
    private Account account(AccountStatus status) {
        Account account = Account.createUser(
                "owner@test.com", "encoded-pw", "김사장", "사장닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(account, "status", status);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));
        return account;
    }

    // OwnerInfo도 실제 엔티티 — isAwaitingReview()가 실제 전이 결과로 계산되게 한다.
    private OwnerInfo ownerInfo(Account account) {
        OwnerInfo ownerInfo = OwnerInfo.create(account, "123-45-67890", LocalDate.of(2020, 1, 1));
        when(ownerInfoRepository.findByAccountIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(ownerInfo));
        return ownerInfo;
    }

    // 체크리스트 전 항목을 통과한 Store
    private Store completedStore(Account account) {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getName()).thenReturn("이음 카페");
        when(store.getAddress()).thenReturn("서울시 어딘가 1-2");
        when(store.getPhone()).thenReturn("02-000-0000");
        when(store.getCategory()).thenReturn(mock(Category.class));
        when(store.getAccount()).thenReturn(account);

        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(productRepository.existsByStore_StoreIdAndProductType(STORE_ID, ProductType.MENU))
                .thenReturn(true);
        when(storeBusinessHourRepository.countByStore_StoreId(STORE_ID)).thenReturn(7L);
        when(settlementAccountRepository.existsByStore_StoreId(STORE_ID)).thenReturn(true);
        return store;
    }

    // ─────────────────── 중복 접수 ───────────────────

    @Test
    void requestReview_이미_접수된_요청은_중복_접수되지_않는다() {
        // given: PENDING + 접수 시각 = 심사 대기 중
        Account account = account(AccountStatus.ACTIVE);
        OwnerInfo ownerInfo = ownerInfo(account);
        ownerInfo.requestReview();
        LocalDateTime firstRequestedAt = ownerInfo.getReviewRequestedAt();

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_ALREADY_REQUESTED);

        // 신청 시각 갱신(대기열 새치기)도, 관리자 알림 재발행도 없어야 한다
        assertThat(ownerInfo.getReviewRequestedAt()).isEqualTo(firstRequestedAt);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestReview_미접수_신청은_접수되고_관리자_알림이_발행된다() {
        // given: PENDING + 접수 시각 없음 = 아직 제출 안 함
        Account account = account(AccountStatus.ACTIVE);
        OwnerInfo ownerInfo = ownerInfo(account);
        completedStore(account);

        // when
        ownerApprovalService.requestReview(ACCOUNT_ID);

        // then
        assertThat(ownerInfo.isAwaitingReview()).isTrue();
        verify(eventPublisher).publishEvent(
                new OwnerApplicationSubmittedEvent(ACCOUNT_ID, "김사장", "이음 카페"));
    }

    @Test
    void requestReview_거절된_신청은_다시_접수할_수_있다() {
        // given: 거절 후에도 접수 시각은 남지만 REJECTED는 재신청 대상이다
        Account account = account(AccountStatus.ACTIVE);
        OwnerInfo ownerInfo = ownerInfo(account);
        ownerInfo.requestReview();
        ownerInfo.reject("서류 미비");
        completedStore(account);

        // when
        ownerApprovalService.requestReview(ACCOUNT_ID);

        // then: PENDING으로 돌아가고 거절 사유는 지워진다
        assertThat(ownerInfo.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(ownerInfo.getRejectionReason()).isNull();
        assertThat(ownerInfo.isAwaitingReview()).isTrue();
    }

    @Test
    void requestReview_승인된_계정은_다시_접수할_수_없다() {
        // given
        Account account = account(AccountStatus.ACTIVE);
        OwnerInfo ownerInfo = ownerInfo(account);
        ownerInfo.requestReview();
        ownerInfo.approve();

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);

        assertThat(ownerInfo.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── 계정 상태별 오류 계약 ───────────────────

    @Test
    void requestReview_정지된_계정은_정지로_거절한다() {
        // given: !isActive()를 한 덩어리로 묶으면 정지 계정이 "가입 미완료"로 응답된다
        account(AccountStatus.SUSPENDED);

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(ownerInfoRepository, never()).findByAccountIdWithLock(anyLong());
    }

    @Test
    void requestReview_탈퇴한_계정은_탈퇴로_거절한다() {
        // given
        account(AccountStatus.WITHDRAWN);

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
    }

    @Test
    void requestReview_가입_미완료_계정은_가입_미완료로_거절한다() {
        // given
        account(AccountStatus.PENDING);

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);

        // 계정 잠금 단계에서 끝난다 — OwnerInfo까지 가지 않는다
        verify(ownerInfoRepository, never()).findByAccountIdWithLock(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── 잠금 규약 ───────────────────

    @Test
    void requestReview_승인과_같은_잠금_순서를_쓴다() {
        // given: 잠금 없는 findByAccount_AccountId로 읽으면 관리자 승인과 last-writer-wins가 되고,
        // 순서가 관리자 경로와 갈리면 교착이 난다.
        Account account = account(AccountStatus.ACTIVE);
        ownerInfo(account);
        completedStore(account);

        // when
        ownerApprovalService.requestReview(ACCOUNT_ID);

        // then: account → owner_info 순서로 고정
        InOrder inOrder = inOrder(accountRepository, ownerInfoRepository);
        inOrder.verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        inOrder.verify(ownerInfoRepository).findByAccountIdWithLock(ACCOUNT_ID);

        verify(ownerInfoRepository, never()).findByAccount_AccountId(anyLong());
    }
}
