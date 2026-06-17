package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.EventProductStatus;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerStoreWithdrawalService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;

    // 사장 회원 탈퇴 시 상점/상품/이벤트 상품을 노출되지 않도록 비활성화 실제 물리 삭제는 30일 후 영구 삭제 스케줄러에서 처리

    @Transactional
    public void deactivateForWithdrawal(Long accountId) {
        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElse(null);

        if (store == null) {
            log.info("사장 탈퇴 처리 중 상점 없음: accountId={}", accountId);
            return;
        }

        Long storeId = store.getStoreId();

        // 사장 탈퇴 후 사용자 화면에서 영업 가능 상태로 보이지 않도록 마감 처리
        store.close();

        // 상점 상품 비활성화
        List<Product> products = productRepository.findByStore_StoreId(storeId);
        products.forEach(Product::deactivate);

        // ACTIVE 이벤트 상품만 종료 (이미 ENDED/DELETED인 것은 제외)
        List<EventProduct> eventProducts = eventProductRepository
                .findByProduct_Store_StoreIdAndStatusOrderByCreatedAtDesc(storeId, EventProductStatus.ACTIVE);
        eventProducts.forEach(EventProduct::delete);

        log.info(
                "사장 탈퇴에 따른 상점 비활성화 완료: accountId={}, storeId={}, productCount={}, eventProductCount={}",
                accountId,
                storeId,
                products.size(),
                eventProducts.size()
        );
    }
}