package com.eeum.eeum.application.ai.dto.response;

import java.math.BigDecimal;

public record AiPlanPaymentCancellationPlan(
        String paymentId,
        BigDecimal amount,
        String idempotencyKey,
        boolean shouldCallPortOne
) {}
