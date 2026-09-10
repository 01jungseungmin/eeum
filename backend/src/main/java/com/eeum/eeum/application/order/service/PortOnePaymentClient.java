package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;

import java.math.BigDecimal;

public interface PortOnePaymentClient {

    PortOnePaymentInfo getPayment(String paymentId);

    /**
     * 결제를 취소한다. 응답의 취소 상태와 식별자를 그대로 돌려준다 —
     * 호출부가 {@code SUCCEEDED}만 취소 완료로 확정할 수 있어야 한다.
     */
    default PortOneCancelResult cancelPayment(String paymentId, BigDecimal amount, String reason) {
        return cancelPayment(paymentId, amount, reason, null);
    }

    PortOneCancelResult cancelPayment(String paymentId, BigDecimal amount, String reason, String idempotencyKey);
}
