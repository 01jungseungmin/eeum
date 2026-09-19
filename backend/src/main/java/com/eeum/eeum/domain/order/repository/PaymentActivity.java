package com.eeum.eeum.domain.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결제 완료 활동 피드용 원값. */
public record PaymentActivity(
        Long paymentId,
        String storeName,
        BigDecimal amount,
        LocalDateTime paidAt
) {
}
