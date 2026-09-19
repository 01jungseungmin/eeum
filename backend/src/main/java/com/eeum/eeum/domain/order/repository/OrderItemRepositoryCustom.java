package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderItemRepositoryCustom {

    /**
     * 상점의 상품별 판매 수량 합계를 수량 내림차순으로 조회한다.
     *
     * @param statuses 집계 대상 주문 상태. 비우지 않는다 — 빈 IN 절은 항상 0건이다
     * @param from 주문 생성 시각 하한(포함). null이면 제한 없음
     * @param to 주문 생성 시각 상한(포함). null이면 제한 없음
     */
    List<ProductSalesQuantity> aggregateSoldQuantityByProduct(
            Long storeId,
            Collection<OrderStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    );

    /**
     * 상점의 상품 카테고리별 판매 수량·금액 합계를 수량 내림차순으로 조회한다.
     * 집계 규칙(이벤트 판매분 합산, 삭제 상품 제외, 기간 기준)은 상품별 집계와 같다.
     *
     * @param statuses 집계 대상 주문 상태. 비우지 않는다 — 빈 IN 절은 항상 0건이다
     * @param from 주문 생성 시각 하한(포함). null이면 제한 없음
     * @param to 주문 생성 시각 상한(포함). null이면 제한 없음
     */
    List<CategorySalesStat> aggregateSalesByCategory(
            Long storeId,
            Collection<OrderStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    );
}
