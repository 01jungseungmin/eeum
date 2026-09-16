package com.eeum.eeum.domain.order.event;

import java.math.BigDecimal;

// 온라인 결제가 완료되었을 때 발행. 수신자는 주문한 사용자
// NotificationType.PAYMENT_COMPLETED 알림으로 변환

public record OrderPaidEvent(
        Long customerAccountId,
        String storeName,
        String orderNumber,
        BigDecimal amount,
        Long orderId
) {
}
