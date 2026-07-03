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

    @Getter
    @Builder
    @Schema(description = "고객 케어 카드 요약")
    public static class CustomerCareSummaryDto {
        @Schema(description = "케어 유형", example = "CART_INTEREST")
        private final String careType;
        @Schema(description = "제목", example = "구매 관심이 높은 고객")
        private final String title;
        @Schema(description = "대상 고객 수", example = "5")
        private final int targetCustomerCount;
    }

    @Getter
    @Builder
    @Schema(description = "리뷰/문의 요약")
    public static class ReviewInquirySummaryDto {
        @Schema(description = "미답변 리뷰 수")
        private final long unansweredReviewCount;
        @Schema(description = "미답변 문의 수")
        private final long unansweredInquiryCount;
        @Schema(description = "반복 불만 키워드 수")
        private final int complaintKeywordCount;
    }

    @Getter
    @Builder
    @Schema(description = "이벤트 성과 요약")
    public static class EventPerformanceSummaryDto {
        @Schema(description = "상품 조회수 (수집 불가 시 null)")
        private final Long productViewCount;
        @Schema(description = "주문 전환율 (계산 불가 시 null)")
        private final Double orderConversionRate;
        @Schema(description = "신규 고객 비중 (계산 불가 시 null)")
        private final Double newCustomerRatio;
        @Schema(description = "단골 재주문 수")
        private final long regularReorderCount;
    }

    @Getter
    @Builder
    @Schema(description = "운영 위험 요약")
    public static class OperationRiskSummaryDto {
        @Schema(description = "종합 위험 신호", example = "NORMAL")
        private final AiRiskLevel riskLevel;
        @Schema(description = "요약 문구")
        private final String headline;
    }

    @Getter
    @Builder
    @Schema(description = "AI 활동 요약")
    public static class ActivitySummaryDto {
        @Schema(description = "최근 30일 초안 생성 수")
        private final long draftCount;
        @Schema(description = "최근 30일 발송 처리 수")
        private final long sentCount;
        @Schema(description = "하이라이트 문구")
        private final String highlight;
    }
}
