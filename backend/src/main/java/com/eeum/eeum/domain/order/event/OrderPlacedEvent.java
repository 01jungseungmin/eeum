package com.eeum.eeum.domain.order.event;

// 주문이 확정(현장결제는 생성 시, 온라인은 결제 완료 시)되었을 때 발행
// 수신자는 가게 사장  NotificationType.NEW_ORDER 알림으로 변환

public record OrderPlacedEvent(
        Long ownerAccountId,
        String customerName,
        String storeName,
        String orderNumber,
        Long orderId
) {
}
