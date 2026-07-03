package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiPlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@Schema(description = "AI 플랜 관리")
public class AiPlanResponseDto {

    @Schema(description = "현재 플랜", example = "BASIC")
    private final AiPlanType currentPlan;

    @Schema(description = "플랜 목록")
    private final List<PlanInfoDto> plans;

    @Schema(description = "이번 달 사용량", example = "12")
    private final long currentUsage;

    @Schema(description = "월 AI 추천 제한 (무제한이면 null)", example = "30")
    private final Integer monthlyLimit;

    @Getter
    @Builder
    @Schema(description = "플랜 정보")
    public static class PlanInfoDto {
        @Schema(description = "플랜 타입", example = "BASIC")
        private final AiPlanType planType;
        @Schema(description = "플랜명", example = "AI Basic")
        private final String displayName;
        @Schema(description = "월 가격", example = "19000")
        private final BigDecimal monthlyPrice;
        @Schema(description = "기능 목록")
        private final List<String> features;
    }
}
