package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "운영 위험 조기정보")
public class AiOperationRiskResponseDto {

    @Schema(description = "종합 위험 신호", example = "NORMAL")
    private final AiRiskLevel overallRiskLevel;

    @Schema(description = "동네 에너지 경기 신호")
    private final String energySignal;

    @Schema(description = "계절/시기 선제 알림")
    private final String seasonalAlert;

    @Schema(description = "업종 활동 이상 변화 감지")
    private final String activityAnomaly;

    @Schema(description = "안전 리스크 체크")
    private final String safetyCheck;

    @Schema(description = "AI 판단")
    private final String aiJudgement;

    @Schema(description = "준비된 대응 체크리스트")
    private final List<String> checklist;

    @Schema(description = "데이터 출처 투명성")
    private final List<DataSourceDto> dataSources;

    @Schema(description = "대표 데이터 출처 타입", example = "LOCAL_AVERAGE_ONLY")
    private final AiDataSourceType sourceType;

    @Schema(description = "실측 기반 데이터 보유 여부 — false면 추정 분석", example = "false")
    private final boolean hasPreciseData;

    @Schema(description = "분석 근거 고지 문구", example = "실시간 측정값이 아닌 추정 분석입니다.")
    private final String notice;

    @Schema(description = "저장된 절감 계획 존재 여부", example = "false")
    private final boolean hasSavedPlan;

    @Getter
    @Builder
    @Schema(description = "데이터 출처")
    public static class DataSourceDto {
        @Schema(description = "출처 타입", example = "OWNER_INPUT")
        private final AiDataSourceType sourceType;
        @Schema(description = "출처 라벨", example = "사장님 입력값")
        private final String sourceLabel;
        @Schema(description = "설명")
        private final String description;

        public static DataSourceDto from(AiDataSourceType type) {
            return DataSourceDto.builder()
                    .sourceType(type)
                    .sourceLabel(type.getSourceLabel())
                    .description(type.getDescription())
                    .build();
        }
    }
}
