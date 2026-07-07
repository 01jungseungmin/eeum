package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "AI 플랜 구독 결제 요청/결과")
public record AiPlanSubscribeResponseDto(
        @Schema(description = "PortOne 결제 ID — 프론트 결제 SDK에 전달", example = "ai-plan-1-a1b2c3d4")
        String paymentId,

        @Schema(description = "구독 플랜", example = "BASIC")
        AiPlanType planType,

        @Schema(description = "결제 금액", example = "19000")
        BigDecimal amount,

        @Schema(description = "결제 상태", example = "PENDING")
        AiPlanPaymentStatus status
) {
}
