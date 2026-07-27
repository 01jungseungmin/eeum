package com.eeum.eeum.application.ai.dto.request;

import com.eeum.eeum.domain.ai.enums.AiMetricType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "사장님 실측값 입력 요청")
public class AiOwnerMetricInputRequestDto {

    @NotNull
    @Schema(description = "실측값 유형", example = "MONTHLY_POWER_KWH")
    private AiMetricType metricType;

    @NotNull
    @PositiveOrZero
    @Schema(description = "값", example = "850.5")
    private BigDecimal value;

    @NotNull
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "yyyy-MM 형식이어야 합니다")
    @Schema(description = "대상 연월", example = "2026-07")
    private String yearMonth;
}
