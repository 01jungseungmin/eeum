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

    /**
     * 후기 응답.
     *
     * <p>후기 자체는 게시글이 삭제·숨김돼도 노출한다 — 거르면 판매자가 나쁜 후기가 달린 글을
     * 지워 평판을 세탁할 수 있다. 다만 비공개 게시글의 <b>제목은 제3자에게 감춘다.</b>
     * 관리자가 숨긴 글의 제목이 후기 목록을 통해 그대로 새어 나가면 숨김 조치가 무의미해진다.
     *
     * <p><b>당사자에게는 감추지 않는다 — 후기 작성자와 게시글 소유자 둘 다.</b>
     * 감추는 목적이 "비공개 글의 내용이 제3자에게 새어 나가는 것"을 막는 것인데,
     * 작성자는 그 글을 보고 후기를 쓴 사람이고 판매자는 그 글의 주인이라 숨길 것이 없다.
     * 오히려 감추면 각자 자기 화면에서 대상을 알 수 없게 된다 —
     * 작성자는 내가 쓴 후기 목록이, 판매자는 자기가 받은 후기 목록이 전부 제목 없이 나온다.
     * (숨김 글은 상세 조회에서 이미 소유자에게 열려 있다. UsedProductService.getVisibleOrThrow 참고 —
     * 여기서만 막으면 같은 판매자가 경로에 따라 다른 것을 보게 된다.)
     *
     * @param viewerId 조회 주체. 비회원 조회에서는 null이다.
     */
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
