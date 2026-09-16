package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "운영 위험 요약")
    public class OperationRiskSummaryDto {
        @Schema(description = "종합 위험 신호", example = "NORMAL")
        private final AiRiskLevel riskLevel;
        @Schema(description = "요약 문구")
        private final String headline;
    }