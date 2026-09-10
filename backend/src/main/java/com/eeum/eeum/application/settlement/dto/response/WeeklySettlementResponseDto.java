package com.eeum.eeum.application.settlement.dto.response;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WeeklySettlementResponseDto(Long weeklySettlementId, Long storeId, LocalDateTime periodStartAt,
                                          LocalDateTime periodEndAt, BigDecimal payoutAmount,
                                          WeeklySettlementStatus status, LocalDateTime payoutCompletedAt) {
    public static WeeklySettlementResponseDto from(WeeklySettlement settlement) {
        return new WeeklySettlementResponseDto(settlement.getWeeklySettlementId(), settlement.getStore().getStoreId(),
                settlement.getPeriodStartAt(), settlement.getPeriodEndAt(), settlement.getPayoutAmount(),
                settlement.getStatus(), settlement.getPayoutCompletedAt());
    }
}
