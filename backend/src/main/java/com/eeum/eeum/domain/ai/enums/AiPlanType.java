package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum AiPlanType {
    FREE("Free", BigDecimal.ZERO),
    BASIC("AI Basic", new BigDecimal("19000")),
    PRO("AI Pro", new BigDecimal("39000"));

    private final String displayName;
    private final BigDecimal monthlyPrice;

    public boolean isAtLeast(AiPlanType required) {
        return this.ordinal() >= required.ordinal();
    }
}
