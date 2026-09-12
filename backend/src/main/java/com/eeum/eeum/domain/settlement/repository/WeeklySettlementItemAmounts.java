package com.eeum.eeum.domain.settlement.repository;

import java.math.BigDecimal;

/** 한 주간 정산에 포함된 항목들의 금액 합계. 지급 직전 대사에 쓴다. */
public record WeeklySettlementItemAmounts(
        BigDecimal paymentAmount,
        BigDecimal pgFeeAmount,
        BigDecimal platformFeeAmount,
        BigDecimal payoutAmount
) {
}
