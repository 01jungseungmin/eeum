package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.product.enums.ProductType;

/**
 * 상품별 판매 수량 집계 원값.
 *
 * 이벤트 상품 판매분은 원본 상품으로 합산되어 한 행으로 돌아온다 —
 * order_item은 일반 판매를 product_id에, 이벤트 판매를 event_product_id에 나눠 담는다.
 */
public record ProductSalesQuantity(
        Long productId,
        String productName,
        ProductType productType,
        Long soldQuantity
) {
}
