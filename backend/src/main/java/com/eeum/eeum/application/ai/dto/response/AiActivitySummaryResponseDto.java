package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 활동 요약")
public class AiActivitySummaryResponseDto {

    @Schema(description = "집계 기간", example = "최근 30일")
    private final String period;

    @Schema(description = "리뷰 답글 초안 수", example = "5")
    private final long reviewReplyDraftCount;

    @Schema(description = "문의 답변 초안 수", example = "3")
    private final long inquiryReplyDraftCount;

    @Schema(description = "단골 메시지 수", example = "2")
    private final long regularMessageCount;

    @Schema(description = "이탈 고객 알림 수", example = "1")
    private final long inactiveAlertCount;

    @Schema(description = "전체 AI 초안 생성 수 (모든 메시지 유형 합산)", example = "5")
    private final long draftCount;

    @Schema(description = "단골 메시지 발송 후 재방문 수 (계산 불가 시 null)")
    private final Long revisitAfterMessageCount;

    @Schema(description = "이벤트 알림 후 주문 전환 수 (계산 불가 시 null)")
    private final Long orderConversionAfterEventCount;

    @Schema(description = "실제 발송 완료 메시지 수", example = "3")
    private final long sentCount;

    @Schema(description = "미답변 문의 유지 수", example = "2")
    private final long unansweredInquiryRemainingCount;

    @Schema(description = "AI 활동 하이라이트 문구")
    private final String highlight;
}
