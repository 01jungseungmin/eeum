package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@Schema(description = "전력 사용 리포트")
public class AiElectricityReportResponseDto {

    @Schema(description = "최근 6개월 월별 사용량")
    private final List<MonthlyUsageDto> monthlyUsages;

    @Schema(description = "설비별 사용 비중 (실측 기반 데이터 없으면 빈 리스트)")
    private final List<EquipmentShareDto> equipmentShares;

    @Schema(description = "핵심 진단")
    private final String keyDiagnosis;

    @Schema(description = "분석 기간", example = "2026-02 ~ 2026-07")
    private final String analysisPeriod;

    @Schema(description = "업종 비교 (데이터 없으면 null)")
    private final String industryComparison;

    @Schema(description = "추정 절감액 (데이터 없으면 null)")
    private final BigDecimal estimatedSavingAmount;

    @Schema(description = "데이터 출처 타입", example = "OWNER_INPUT")
    private final AiDataSourceType sourceType;

    @Schema(description = "데이터 존재 여부", example = "false")
    private final boolean hasData;

    @Schema(description = "데이터 없음 안내 문구")
    private final String emptyMessage;

    @Getter
    @Builder
    @Schema(description = "월별 사용량")
    public static class MonthlyUsageDto {
        @Schema(description = "연월", example = "2026-07")
        private final String yearMonth;
        @Schema(description = "사용량 (kWh)")
        private final BigDecimal kwh;
    }

    @Getter
    @Builder
    @Schema(description = "설비별 사용 비중")
    public static class EquipmentShareDto {
        @Schema(description = "설비명", example = "냉방·공조")
        private final String name;
        @Schema(description = "비중 (%)", example = "38")
        private final int ratio;
    }
}
