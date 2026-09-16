package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "생활권 매칭 분석")
public class AiLocalMatchResponseDto {

    @Schema(description = "종합 매칭 점수 (0~100, 데이터 부족 시 null)")
    private final Integer totalScore;

    @Schema(description = "지역 일치율 (0~100)")
    private final Integer regionMatchRate;

    @Schema(description = "관심사 일치율 (0~100)")
    private final Integer interestMatchRate;

    @Schema(description = "이벤트 적합도 (0~100)")
    private final Integer eventFitScore;

    @Schema(description = "단골 고객 비중 (0~100)")
    private final Integer regularCustomerRatio;

    @Schema(description = "노출 대상 고객 세그먼트")
    private final List<String> segments;

    @Schema(description = "매칭 이유")
    private final String matchReason;

    @Schema(description = "예상 노출 대상 수", example = "34")
    private final int estimatedTargetCount;

    @Schema(description = "현재 노출 조건 — 반경(km)", example = "1.5")
    private final double radiusKm;

    @Schema(description = "현재 노출 조건 — 관심사", example = "한식")
    private final String interest;

    @Schema(description = "현재 노출 조건 — 고객 유형", example = "ALL")
    private final AiCustomerType customerType;

    @Schema(description = "데이터 존재 여부", example = "true")
    private final boolean hasData;

    @Schema(description = "데이터 없음 안내 문구")
    private final String emptyMessage;
}
