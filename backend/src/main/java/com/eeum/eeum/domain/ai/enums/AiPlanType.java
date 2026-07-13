package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum AiPlanType {
    FREE("Free", BigDecimal.ZERO),
    BASIC("AI Basic", new BigDecimal("9900")),
    PRO("AI Pro", new BigDecimal("19900"));

    private final String displayName;
    private final BigDecimal monthlyPrice;

    public boolean isAtLeast(AiPlanType required) {
        return this.ordinal() >= required.ordinal();
    }
}
