package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.domain.store.entity.StoreReview;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "사장용 상점 리뷰 목록 응답")
public class OwnerStoreReviewResponseDto {

    @Schema(description = "리뷰 ID", example = "1")
    private Long storereviewId;

    @Schema(description = "작성자 account ID", example = "10")
    private Long accountId;

    @Schema(description = "작성자 닉네임", example = "동네주민A")
    private String nickname;

    @Schema(description = "별점 (1~5)", example = "5")
    private int rating;

    @Schema(description = "리뷰 내용 (앞 100자)", example = "음식이 정말 맛있었어요!")
    private String content;

    @Schema(description = "이미지 수", example = "2")
    private int imageCount;

    @Schema(description = "답글 작성 여부", example = "false")
    private boolean hasReply;

    @Schema(description = "리뷰 작성일시")
    private LocalDateTime createdAt;

    public static OwnerStoreReviewResponseDto of(StoreReview review, int imageCount, boolean hasReply) {
        return OwnerStoreReviewResponseDto.builder()
                .storereviewId(review.getStorereviewId())
                .accountId(review.getAccount().getAccountId())
                .nickname(review.getAccount().getNickname())
                .rating(review.getRating())
                .content(review.getContent())
                .imageCount(imageCount)
                .hasReply(hasReply)
                .createdAt(review.getCreatedAt())
                .build();
    }
}