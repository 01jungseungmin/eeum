package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductImageRepository;
import com.eeum.eeum.domain.product.repository.ProductOptionItemRepository;
import com.eeum.eeum.domain.product.repository.ProductOptionRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상점 물리 삭제 시 다른 사용자가 남긴 찜 정리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class StorePhysicalDeleteServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long STORE_ID = 7L;

    @Mock private StoreRepository storeRepository;
    @Mock private FavoriteService favoriteService;
    @Mock private ProductRepository productRepository;
    @Mock private EventProductRepository eventProductRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductOptionRepository productOptionRepository;
    @Mock private ProductOptionItemRepository productOptionItemRepository;
    @Mock private ProductCategoryRepository productCategoryRepository;
    @Mock private StoreImageRepository storeImageRepository;
    @Mock private StoreNoticeRepository storeNoticeRepository;
    @Mock private SettlementAccountRepository settlementAccountRepository;

    @InjectMocks
    private StorePhysicalDeleteService storePhysicalDeleteService;

    @Test
    void 상점을_지우기_전에_다른_사용자의_찜을_먼저_정리한다() {
        // given — Favorite은 FK 없는 polymorphic 참조라 상점만 지우면 행이 그대로 남아
        // 전체 찜 목록·찜 여부 조회에 사라진 상점을 가리키는 항목이 계속 나온다
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.of(store));
        when(productRepository.findByStore_StoreId(STORE_ID)).thenReturn(List.of());

        // when
        storePhysicalDeleteService.deleteStoreDataByAccountId(ACCOUNT_ID);

        // then
        InOrder inOrder = inOrder(favoriteService, storeRepository);
        inOrder.verify(favoriteService)
                .deleteAllByRefTypeAndRefId(FavoriteRefType.STORE, STORE_ID);
        inOrder.verify(storeRepository).delete(store);
    }

    @Test
    void 상점이_없으면_아무것도_지우지_않는다() {
        when(storeRepository.findByAccount_AccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        storePhysicalDeleteService.deleteStoreDataByAccountId(ACCOUNT_ID);

        verify(favoriteService, never()).deleteAllByRefTypeAndRefId(any(), any());
        verify(storeRepository, never()).delete(any());
    }
}
