package com.eeum.eeum.domain.product.event;

// 상품 재고가 경고 임계값 이하로 떨어졌을 때 발행 수신자는 가게 사장
// NotificationType.STOCK_WARNING 알림으로 변환

public record ProductStockWarningEvent(
        Long ownerAccountId,
        String storeName,
        String productName,
        int remainingStock,
        Long productId
) {
}
