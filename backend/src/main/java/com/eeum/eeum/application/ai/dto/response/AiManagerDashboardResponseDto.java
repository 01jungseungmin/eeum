package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "AI 매니저 메인 대시보드")
public class AiManagerDashboardResponseDto {

    @Schema(description = "보고 기준 시각")
    private final LocalDateTime reportedAt;

    @Schema(description = "오늘 처리할 항목 수", example = "6")
    private final long todoCount;

    @Schema(description = "개인정보 안내 문구")
    private final String privacyNotice;

    @Schema(description = "AI 고객 케어 카드 3개 요약")
    private final List<CustomerCareSummaryDto> customerCareSummaries;

    @Schema(description = "리뷰/문의 자동 대응 요약")
    private final ReviewInquirySummaryDto reviewInquirySummary;

    @Schema(description = "이벤트 성과 요약")
    private final EventPerformanceSummaryDto eventPerformanceSummary;

    @Schema(description = "생활권 매칭 점수 (데이터 부족 시 null)")
    private final Integer localMatchScore;

    @Schema(description = "운영 위험 조기정보 요약")
    private final OperationRiskSummaryDto operationRiskSummary;

    @Schema(description = "AI 활동 요약")
    private final ActivitySummaryDto activitySummary;
}
