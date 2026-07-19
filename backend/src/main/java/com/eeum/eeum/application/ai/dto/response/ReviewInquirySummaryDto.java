package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "리뷰/문의 요약")
    public class ReviewInquirySummaryDto {
        @Schema(description = "미답변 리뷰 수")
        private final long unansweredReviewCount;
        @Schema(description = "미답변 문의 수")
        private final long unansweredInquiryCount;
        @Schema(description = "반복 불만 키워드 수")
        private final int complaintKeywordCount;
    }