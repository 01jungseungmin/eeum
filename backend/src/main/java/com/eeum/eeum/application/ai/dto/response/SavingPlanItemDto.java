package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
    @Builder
    @Schema(description = "절감 항목")
    public class SavingPlanItemDto {
        @Schema(description = "항목명", example = "냉방 시간대 관리")
        private final String title;
        @Schema(description = "난이도", example = "쉬움")
        private final String difficulty;
        @Schema(description = "시작 시점", example = "즉시")
        private final String startTiming;
        @Schema(description = "월 예상 절감액")
        private final BigDecimal expectedMonthlySavingAmount;
        @Schema(description = "선택 여부", example = "true")
        private final boolean selected;
    }