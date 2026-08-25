package com.eeum.eeum.application.used.dto.response;

import com.eeum.eeum.domain.used.entity.UsedReview;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "중고거래 후기")
public class UsedReviewResponseDto {

    @Schema(description = "후기 ID", example = "7")
    private final Long usedReviewId;

    @Schema(description = "대상 게시글 ID", example = "25")
    private final Long usedProductId;

    @Schema(description = "게시글 제목", example = "자전거 팝니다")
    private final String usedProductTitle;

    @Schema(description = "작성자 계정 ID", example = "17")
    private final Long reviewerAccountId;

    @Schema(description = "작성자 닉네임", example = "구매자닉")
    private final String reviewerNickname;

    @Schema(description = "별점 (1~5)", example = "5")
    private final int rating;

    @Schema(description = "후기 내용")
    private final String content;

    @Schema(description = "작성 일시")
    private final LocalDateTime createdAt;

    @Schema(description = "수정 일시")
    private final LocalDateTime modifiedAt;

    public static UsedReviewResponseDto from(UsedReview review) {
        return UsedReviewResponseDto.builder()
                .usedReviewId(review.getUsedReviewId())
                .usedProductId(review.getUsedProduct().getUsedProductId())
                .usedProductTitle(review.getUsedProduct().getTitle())
                .reviewerAccountId(review.getReviewer().getAccountId())
                .reviewerNickname(review.getReviewer().getNickname())
                .rating(review.getRating())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .modifiedAt(review.getModifiedAt())
                .build();
    }
}
