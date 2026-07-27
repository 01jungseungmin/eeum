package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
    @Builder
    @Schema(description = "월별 사용량")
    public class MonthlyUsageDto {
        @Schema(description = "연월", example = "2026-07")
        private final String yearMonth;
        @Schema(description = "사용량 (kWh)")
        private final BigDecimal kwh;
    }