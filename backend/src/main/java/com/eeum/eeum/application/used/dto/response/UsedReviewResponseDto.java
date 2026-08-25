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
     * 지워 평판을 세탁할 수 있다. 다만 비공개 게시글의 <b>제목은 제3자에게 감춘다.</b>
     * 관리자가 숨긴 글의 제목이 후기 목록을 통해 그대로 새어 나가면 숨김 조치가 무의미해진다.
     *
     * <p><b>작성자 본인에게는 감추지 않는다.</b> 감추는 목적이 "비공개 글의 내용이 제3자에게
     * 새어 나가는 것"을 막는 것인데, 작성자는 그 글을 보고 후기를 쓴 사람이라 숨길 것이 없다.
     * 오히려 감추면 판매자가 글을 지운 뒤 작성자가 자기 후기의 대상을 알 수 없게 된다
     * (내가 쓴 후기 목록이 전부 제목 없이 나온다).
     *
     * @param viewerId 조회 주체. 비회원 조회에서는 null이다.
     */
    public static UsedReviewResponseDto from(UsedReview review, Long viewerId) {
        boolean visible = review.getUsedProduct().isPubliclyVisible()
                || (viewerId != null && review.isWrittenBy(viewerId));
        return UsedReviewResponseDto.builder()
                .usedReviewId(review.getUsedReviewId())
                .usedProductId(review.getUsedProduct().getUsedProductId())
                // 게시글 공개 여부는 사실 그대로 내린다. 작성자에게 제목을 보여주더라도
                // 그 글이 비공개라는 사실은 알려야 프론트가 상세 이동을 막을 수 있다.
                .usedProductVisible(review.getUsedProduct().isPubliclyVisible())
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
