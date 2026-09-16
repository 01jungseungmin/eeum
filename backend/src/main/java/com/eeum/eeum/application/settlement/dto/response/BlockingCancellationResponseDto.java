package com.eeum.eeum.application.settlement.dto.response;

import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "정산 지급을 막고 있는 미완료 취소 작업")
public record BlockingCancellationResponseDto(
        Long orderId,
        String orderNumber,
        PaymentCancellationStatus status,
        String failureCode,
        String failureReason,
        LocalDateTime requestedAt
) {
    public static BlockingCancellationResponseDto from(PaymentCancellationOperation operation) {
        return new BlockingCancellationResponseDto(
                operation.getOrder().getOrderId(),
                operation.getOrder().getOrderNumber(),
                operation.getStatus(),
                operation.getFailureCode(),
                operation.getFailureReason(),
                operation.getRequestedAt());
    }
}
