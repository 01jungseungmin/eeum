package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "리뷰/문의 자동 대응 현황")
public class AiReviewInquiryResponseDto {

    @Schema(description = "최근 2주 반복 불만 키워드")
    private final List<String> complaintKeywords;

    @Schema(description = "미답변 리뷰 수", example = "3")
    private final long unansweredReviewCount;

    @Schema(description = "미답변 문의 수", example = "2")
    private final long unansweredInquiryCount;

    @Schema(description = "미답변 리뷰 목록")
    private final List<UnansweredReviewDto> unansweredReviews;

    @Schema(description = "미답변 문의 목록")
    private final List<UnansweredInquiryDto> unansweredInquiries;

    @Schema(description = "AI 추천 대응 문구")
    private final String recommendedResponse;

    @Schema(description = "데이터 존재 여부", example = "true")
    private final boolean hasData;

    @Schema(description = "데이터 없음 안내 문구")
    private final String emptyMessage;

    @Getter
    @Builder
    @Schema(description = "미답변 리뷰 요약")
    public static class UnansweredReviewDto {
        @Schema(description = "리뷰 ID")
        private final Long reviewId;
        @Schema(description = "평점 (1~5)")
        private final int rating;
        @Schema(description = "리뷰 내용")
        private final String content;
        @Schema(description = "작성 시각")
        private final LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @Schema(description = "미답변 문의 요약")
    public static class UnansweredInquiryDto {
        @Schema(description = "문의 ID")
        private final Long inquiryId;
        @Schema(description = "문의 제목")
        private final String title;
        @Schema(description = "작성 시각")
        private final LocalDateTime createdAt;
    }
}
