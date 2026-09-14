package com.eeum.eeum.application.used.dto.response;

import com.eeum.eeum.domain.used.entity.UsedProduct;
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

    @Schema(description = "게시글 제목. 비공개(삭제·숨김·판매자 탈퇴) 게시글이면 null이지만, "
            + "거래 당사자(후기 작성자·게시글 판매자)에게는 비공개여도 제목을 내려준다",
            example = "자전거 팝니다")
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

    /** 비공개 게시글의 제목은 제3자에게만 감추고, 후기 자체는 평판 보호를 위해 노출한다. */
    public static UsedReviewResponseDto from(UsedReview review, Long viewerId) {
        UsedProduct product = review.getUsedProduct();
        boolean party = viewerId != null
                && (review.isWrittenBy(viewerId) || product.isOwnedBy(viewerId));
        boolean visible = product.isPubliclyVisible() || party;
        return UsedReviewResponseDto.builder()
                .usedReviewId(review.getUsedReviewId())
                .usedProductId(product.getUsedProductId())
                // 게시글 공개 여부는 사실 그대로 내린다. 작성자에게 제목을 보여주더라도
                // 그 글이 비공개라는 사실은 알려야 프론트가 상세 이동을 막을 수 있다.
                .usedProductVisible(product.isPubliclyVisible())
                .usedProductTitle(visible ? product.getTitle() : null)
                .reviewerAccountId(review.getReviewer().getAccountId())
                // getNickname()이 아니라 표시명이다 — nickname은 nullable이라 null이 나갈 수 있다
                .reviewerNickname(review.getReviewer().getDisplayName())
                .rating(review.getRating())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .modifiedAt(review.getModifiedAt())
                .build();
    }
}
