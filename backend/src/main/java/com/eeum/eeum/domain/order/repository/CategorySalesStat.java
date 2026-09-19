package com.eeum.eeum.domain.order.repository;

import java.math.BigDecimal;

/**
 * 상품 카테고리별 판매 집계 원값.
 *
 * 이벤트 상품 판매분은 원본 상품의 카테고리로 합산된다.
 * 판매 금액은 order_item.line_total_price(단가 × 수량) 합이다.
 */
public record CategorySalesStat(
        Long categoryId,
        String categoryName,
        Long soldQuantity,
        BigDecimal salesAmount
) {
}
