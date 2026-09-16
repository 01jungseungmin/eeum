package com.eeum.eeum.domain.order.event;

// 사장이 주문 상태를 변경(승인 등)했을 때 발행. 수신자는 주문한 사용자.
// NotificationType.ORDER_STATUS_CHANGED 알림으로 변환
public record OrderStatusChangedEvent(
        Long customerAccountId,
        String storeName,
        String orderNumber,
        String statusLabel,
        Long orderId
) {
}
