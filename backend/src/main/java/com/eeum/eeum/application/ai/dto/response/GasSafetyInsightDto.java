package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
    @Builder
    @Schema(description = "가스 안전 인사이트")
    public class GasSafetyInsightDto {
        @Schema(description = "공공데이터 기준 최근 가스사고 총 건수 (데이터 없으면 null)", example = "158")
        private final Integer publicAccidentCount;

        @Schema(description = "주요 사고 원인 (데이터 없으면 null)", example = "사용자취급부주의")
        private final String topCause;

        @Schema(description = "주요 원인이 전체에서 차지하는 비율(%) (데이터 없으면 null)", example = "30.4")
        private final Double topCauseRatio;

        @Schema(description = "사장님이 입력한 가스 설비 대수 (미입력 시 null)", example = "1")
        private final Integer gasEquipmentCount;

        @Schema(description = "최근 리뷰/문의에서 감지된 안전 관련 키워드")
        private final List<String> detectedKeywords;

        @Schema(description = "주요 원인/신호에 맞춘 대응 체크리스트")
        private final List<String> recommendedActions;

        @Schema(description = "이 인사이트의 데이터 출처", example = "PUBLIC_DATA")
        private final AiDataSourceType sourceType;
    }