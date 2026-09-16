package com.eeum.eeum.application.store.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewImageRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreReviewPublicVisibilityTest {

    @InjectMocks private StoreReviewService service;
    @Mock private FileStorageService fileStorageService;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private StoreReviewImageRepository storeReviewImageRepository;
    @Mock private StoreReviewReplyRepository storeReviewReplyRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private VisitReservationRepository visitReservationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OwnerInfoRepository ownerInfoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final Long storeId = 10L;
    private final Long ownerId = 20L;
    private Store store;
    private Account owner;

    @BeforeEach
    void setUp() {
        store = mock(Store.class);
        owner = mock(Account.class);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getAccount()).thenReturn(owner);
    }

    @Test
    void 정지된_상점의_공개_리뷰_목록은_노출하지_않는다() {
        when(owner.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(store.getStatus()).thenReturn(StoreStatus.SUSPENDED);

        assertStoreNotFound(() -> service.getReviews(storeId, PageRequest.of(0, 10)));

        verify(storeReviewRepository, never())
                .findByStore_StoreIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 승인되지_않은_사장_상점의_공개_리뷰_상세는_노출하지_않는다() {
        when(owner.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(owner.getAccountId()).thenReturn(ownerId);
        when(store.getStatus()).thenReturn(StoreStatus.OPEN);
        when(ownerInfoRepository.existsByAccount_AccountIdAndApprovalStatus(
                ownerId, ApprovalStatus.APPROVED)).thenReturn(false);

        assertStoreNotFound(() -> service.getReviewDetail(storeId, 30L));

        verify(storeReviewRepository, never())
                .findByStorereviewIdAndStore_StoreId(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 공개가능한_상점은_리뷰_목록을_조회한다() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(owner.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(owner.getAccountId()).thenReturn(ownerId);
        when(store.getStatus()).thenReturn(StoreStatus.OPEN);
        when(ownerInfoRepository.existsByAccount_AccountIdAndApprovalStatus(
                ownerId, ApprovalStatus.APPROVED)).thenReturn(true);
        when(storeReviewRepository.findByStore_StoreIdOrderByCreatedAtDesc(storeId, pageable))
                .thenReturn(Page.empty(pageable));

        assertThat(service.getReviews(storeId, pageable)).isEmpty();
    }

    private void assertStoreNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }
}
