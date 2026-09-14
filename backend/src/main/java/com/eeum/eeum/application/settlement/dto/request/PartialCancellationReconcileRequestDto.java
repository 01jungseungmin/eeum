package com.eeum.eeum.application.settlement.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PartialCancellationReconcileRequestDto(
        @NotNull @DecimalMin(value = "0.01") BigDecimal cumulativeCancelledAmount,
        @NotNull @DecimalMin(value = "0.0") BigDecimal pgFeeRate,
        @NotNull @DecimalMin(value = "0.0") BigDecimal platformFeeRate
) {
}
