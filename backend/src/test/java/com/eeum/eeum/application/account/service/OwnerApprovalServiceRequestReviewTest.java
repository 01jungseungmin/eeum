package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
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
 * 입점 심사 재신청 회귀 테스트.
 *
 * <p>고정하는 계약은 두 가지다.
 * <ul>
 *   <li>이미 접수된 신청은 다시 접수되지 않는다. 재접수를 허용하면 신청 시각이 갱신돼 대기열
 *       순서가 밀리고 관리자 알림이 요청 횟수만큼 재발행된다.</li>
 *   <li>OwnerInfo는 잠금 조회로 읽는다. 잠그지 않으면 관리자 승인이 먼저 커밋된 뒤 이 트랜잭션이
 *       상태를 PENDING으로 되돌려 ROLE_OWNER + PENDING이 남는다(@Version 없음 → 행 전체 덮어쓰기).</li>
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

    private Account activeAccount() {
        Account account = mock(Account.class);
        when(account.isActive()).thenReturn(true);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));
        return account;
    }

    private OwnerInfo lockedOwnerInfo() {
        OwnerInfo ownerInfo = mock(OwnerInfo.class);
        when(ownerInfoRepository.findByAccountIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(ownerInfo));
        return ownerInfo;
    }

    // 체크리스트 전 항목 통과 상태의 Store
    private Store completedStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getName()).thenReturn("이음 카페");
        when(store.getAddress()).thenReturn("서울시 어딘가 1-2");
        when(store.getPhone()).thenReturn("02-000-0000");
        when(store.getCategory()).thenReturn(mock(Category.class));

        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(productRepository.existsByStore_StoreIdAndProductType(STORE_ID, ProductType.MENU))
                .thenReturn(true);
        when(storeBusinessHourRepository.countByStore_StoreId(STORE_ID)).thenReturn(7L);
        when(settlementAccountRepository.existsByStore_StoreId(STORE_ID)).thenReturn(true);
        return store;
    }

    @Test
    void requestReview_이미_접수된_요청은_중복_접수되지_않는다() {
        // given: PENDING + 접수 시각 있음 = 심사 대기 중
        activeAccount();
        OwnerInfo ownerInfo = lockedOwnerInfo();
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(ownerInfo.getApprovalStatus()).thenReturn(ApprovalStatus.PENDING);

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_REVIEW_ALREADY_REQUESTED);

        // 신청 시각 갱신도, 관리자 알림 재발행도 없어야 한다
        verify(ownerInfo, never()).requestReview();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestReview_미접수_신청은_접수되고_관리자_알림이_발행된다() {
        // given: PENDING + 접수 시각 없음 = 아직 제출 안 함
        Account account = activeAccount();
        when(account.getName()).thenReturn("김사장");

        OwnerInfo ownerInfo = lockedOwnerInfo();
        when(ownerInfo.isReviewRequested()).thenReturn(false);
        when(ownerInfo.isBusinessVerified()).thenReturn(true);

        Store store = completedStore();
        when(store.getAccount()).thenReturn(account);

        // when
        ownerApprovalService.requestReview(ACCOUNT_ID);

        // then
        verify(ownerInfo).requestReview();
        verify(eventPublisher).publishEvent(
                new OwnerApplicationSubmittedEvent(ACCOUNT_ID, "김사장", "이음 카페"));
    }

    @Test
    void requestReview_거절된_신청은_다시_접수할_수_있다() {
        // given: 거절 후에도 reviewRequestedAt은 남지만 REJECTED는 재신청 대상이다
        Account account = activeAccount();
        when(account.getName()).thenReturn("김사장");

        OwnerInfo ownerInfo = lockedOwnerInfo();
        when(ownerInfo.isReviewRequested()).thenReturn(true);
        when(ownerInfo.getApprovalStatus()).thenReturn(ApprovalStatus.REJECTED);
        when(ownerInfo.isBusinessVerified()).thenReturn(true);

        Store store = completedStore();
        when(store.getAccount()).thenReturn(account);

        // when
        ownerApprovalService.requestReview(ACCOUNT_ID);

        // then
        verify(ownerInfo).requestReview();
    }

    @Test
    void requestReview_승인된_계정은_다시_접수할_수_없다() {
        // given
        activeAccount();
        OwnerInfo ownerInfo = lockedOwnerInfo();
        when(ownerInfo.isApproved()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWNER_ALREADY_APPROVED);

        verify(ownerInfo, never()).requestReview();
    }

    @Test
    void requestReview_비활성_계정은_접수할_수_없다() {
        // given
        Account account = mock(Account.class);
        when(account.isActive()).thenReturn(false);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> ownerApprovalService.requestReview(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);

        // 계정 잠금 단계에서 끝난다 — OwnerInfo까지 가지 않는다
        verify(ownerInfoRepository, never()).findByAccountIdWithLock(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestReview_OwnerInfo를_잠금_조회로_읽는다() {
        // given: 잠금 없는 findByAccount_AccountId로 읽으면 관리자 승인과 last-writer-wins가 된다
        Account account = activeAccount();
        when(account.getName()).thenReturn("김사장");

        OwnerInfo ownerInfo = lockedOwnerInfo();
        when(ownerInfo.isReviewRequested()).thenReturn(false);
        when(ownerInfo.isBusinessVerified()).thenReturn(true);

        Store store = completedStore();
        when(store.getAccount()).thenReturn(account);

        // when
        ownerApprovalService.requestReview(ACCOUNT_ID);

        // then: 잠금 순서 account → owner_info
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        verify(ownerInfoRepository).findByAccountIdWithLock(ACCOUNT_ID);
        verify(ownerInfoRepository, never()).findByAccount_AccountId(anyLong());
    }
}
