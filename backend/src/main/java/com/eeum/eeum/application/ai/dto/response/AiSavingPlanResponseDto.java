package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@Schema(description = "절감 계획")
public class AiSavingPlanResponseDto {

    @Schema(description = "절감 계획 ID", example = "1")
    private final Long savingPlanId;

    @Schema(description = "계획 제목")
    private final String title;

    @Schema(description = "절감 항목 목록")
    private final List<SavingPlanItemDto> items;

    @Schema(description = "총 예상 절감액 (월)")
    private final BigDecimal totalExpectedSavingAmount;

    @Schema(description = "실행 일정")
    private final String schedule;

    @Schema(description = "상태", example = "DRAFT")
    private final AiSavingPlanStatus status;

    public static AiSavingPlanResponseDto from(AiSavingPlan plan, String schedule) {
        return AiSavingPlanResponseDto.builder()
                .savingPlanId(plan.getAiSavingPlanId())
                .title(plan.getTitle())
                .items(plan.getItems().stream()
                        .map(item -> SavingPlanItemDto.builder()
                                .title(item.getTitle())
                                .difficulty(item.getDifficulty())
                                .startTiming(item.getStartTiming())
                                .expectedMonthlySavingAmount(item.getExpectedMonthlySavingAmount())
                                .selected(item.isSelected())
                                .build())
                        .toList())
                .totalExpectedSavingAmount(plan.getExpectedMonthlySavingAmount())
                .schedule(schedule)
                .status(plan.getStatus())
                .build();
    }
}
