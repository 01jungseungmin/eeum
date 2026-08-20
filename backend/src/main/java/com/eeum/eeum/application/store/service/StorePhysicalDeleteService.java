package com.eeum.eeum.application.store.service;

import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductOption;
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
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorePhysicalDeleteService {

    private final StoreRepository storeRepository;
    private final FavoriteService favoriteService;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionItemRepository productOptionItemRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final SettlementAccountRepository settlementAccountRepository;

    // 계정 영구 삭제 시 연결된 상점 하위 데이터를 물리 삭제
    @Transactional
    public void deleteStoreDataByAccountId(Long accountId) {
        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElse(null);

        if (store == null) {
            return;
        }

        Long storeId = store.getStoreId();

        List<Product> products = productRepository.findByStore_StoreId(storeId);

        for (Product product : products) {
            Long productId = product.getProductId();

            // 이벤트 상품 삭제
            eventProductRepository.deleteByProduct_ProductId(productId);

            // 상품 이미지 삭제
            productImageRepository.deleteByProduct_ProductId(productId);

            // 상품 옵션 항목 삭제 후 옵션 삭제
            List<ProductOption> options =
                    productOptionRepository.findByProduct_ProductIdOrderByDisplayOrderAsc(productId);

            for (ProductOption option : options) {
                productOptionItemRepository.deleteByProductOption_ProductOptionId(
                        option.getProductOptionId()
                );
            }

            productOptionRepository.deleteByProduct_ProductId(productId);
        }

        // 상품 삭제
        productRepository.deleteByStore_StoreId(storeId);

        // 상품 카테고리 삭제
        productCategoryRepository.deleteByStore_StoreId(storeId);

        // 상점 이미지/공지/정산 계좌 삭제
        storeImageRepository.deleteByStore_StoreId(storeId);
        storeNoticeRepository.deleteByStore_StoreId(storeId);
        settlementAccountRepository.deleteByStore_StoreId(storeId);

        // 상점 삭제
        // 다른 사용자가 이 상점에 남긴 찜을 먼저 지운다.
        // Favorite은 FK 없는 polymorphic 참조라 상점만 지우면 행이 그대로 남아,
        // 전체 찜 목록·찜 여부 조회에 사라진 상점을 가리키는 항목이 계속 나온다.
        favoriteService.deleteAllByRefTypeAndRefId(FavoriteRefType.STORE, storeId);

        storeRepository.delete(store);

        log.info("상점 하위 데이터 물리 삭제 완료: accountId={}, storeId={}", accountId, storeId);
    }
}