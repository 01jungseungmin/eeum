package com.eeum.eeum.application.order.dto.response;

import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RefundInfoResponseDto {

    private RefundStatus refundStatus;
    private String refundReason;
    private LocalDateTime refundedAt;

    public static RefundInfoResponseDto from(Payment payment) {
        if (payment == null || payment.getRefundStatus() == null) {
            return null;
        }

        return RefundInfoResponseDto.builder()
                .refundStatus(payment.getRefundStatus())
                .refundReason(payment.getRefundReason())
                .refundedAt(payment.getRefundedAt())
                .build();
    }
}