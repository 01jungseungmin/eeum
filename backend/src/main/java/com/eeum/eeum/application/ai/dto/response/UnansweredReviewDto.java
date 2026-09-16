package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
    @Builder
    @Schema(description = "미답변 리뷰 요약")
    public class UnansweredReviewDto {
        @Schema(description = "리뷰 ID")
        private final Long reviewId;
        @Schema(description = "평점 (1~5)")
        private final int rating;
        @Schema(description = "리뷰 내용")
        private final String content;
        @Schema(description = "작성 시각")
        private final LocalDateTime createdAt;
    }