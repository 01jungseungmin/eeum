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

    @Schema(description = "게시글 제목. 삭제·숨김된 게시글이면 null", example = "자전거 팝니다")
    private final String usedProductTitle;

    @Schema(description = "대상 게시글이 아직 공개 상태인지. false면 게시글로 이동할 수 없다", example = "true")
    private final boolean usedProductVisible;

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

    /**
     * 후기 응답.
     *
     * <p>후기 자체는 게시글이 삭제·숨김돼도 노출한다 — 거르면 판매자가 나쁜 후기가 달린 글을
     * 지워 평판을 세탁할 수 있다. 다만 비공개 게시글의 <b>제목은 감춘다.</b>
     * 관리자가 숨긴 글의 제목이 후기 목록을 통해 그대로 새어 나가면 숨김 조치가 무의미해진다.
     */
    public static UsedReviewResponseDto from(UsedReview review) {
        boolean visible = review.getUsedProduct().isPubliclyVisible();
        return UsedReviewResponseDto.builder()
                .usedReviewId(review.getUsedReviewId())
                .usedProductId(review.getUsedProduct().getUsedProductId())
                .usedProductVisible(visible)
                .usedProductTitle(visible ? review.getUsedProduct().getTitle() : null)
                .reviewerAccountId(review.getReviewer().getAccountId())
                .reviewerNickname(review.getReviewer().getNickname())
                .rating(review.getRating())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .modifiedAt(review.getModifiedAt())
                .build();
    }
}
