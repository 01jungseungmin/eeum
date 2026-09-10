package com.eeum.eeum.application.settlement.dto.response;

import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OwnerRevenueResponseDto(Long ownerRevenueId, BigDecimal paymentAmount, BigDecimal pgFeeAmount,
                                      BigDecimal platformFeeAmount, BigDecimal payoutAmount, OwnerRevenueStatus status,
                                      LocalDateTime settleableAt, LocalDateTime createdAt) {
    public static OwnerRevenueResponseDto from(OwnerRevenue revenue) {
        return new OwnerRevenueResponseDto(revenue.getOwnerRevenueId(), revenue.getPaymentAmount(), revenue.getPgFeeAmount(),
                revenue.getPlatformFeeAmount(), revenue.getPayoutAmount(), revenue.getStatus(), revenue.getSettleableAt(), revenue.getCreatedAt());
    }
}
