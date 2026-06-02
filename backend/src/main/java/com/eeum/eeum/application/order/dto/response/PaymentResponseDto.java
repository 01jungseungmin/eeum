package com.eeum.eeum.application.order.dto.response;

import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponseDto {

    private Long paymentId;
    private Long orderId;
    private String portonePaymentId;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private RefundStatus refundStatus;
    private String refundReason;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;

    public static PaymentResponseDto from(Payment payment) {
        return PaymentResponseDto.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrder().getOrderId())
                .portonePaymentId(payment.getPortonePaymentId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .refundStatus(payment.getRefundStatus())
                .refundReason(payment.getRefundReason())
                .refundedAt(payment.getRefundedAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}