package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중고거래 후기.
 *
 * <p>구매자가 판매자에게 남기는 단방향 후기다. 판매자는 후기를 쓰지 않는다 —
 * 상호 평가는 노쇼·취소 분쟁 처리와 구매자 평판 노출이 함께 필요해 별도 결정 사항이다.
 * UNIQUE에 작성자를 포함해 두었으므로, 나중에 양방향으로 넓힐 때 스키마는 그대로 쓸 수 있다.
 *
 * <p>후기는 Soft Delete 대상이 아니다(삭제하면 물리 삭제한다). 게시글과 달리 다른 도메인이
 * 후기를 참조하지 않는다.
 *
 * <p>게시글이 Soft Delete되어도 후기는 남는다 — 판매완료 글에 후기가 매달려 있다는 것이
 * UsedProduct를 Soft Delete로 둔 이유 자체다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "used_review",
        uniqueConstraints = {
                // 한 거래에 작성자당 후기 1개. 앱 레벨 존재 확인만으로는 동시 요청에 중복이 들어간다.
                // 작성자를 키에 포함해 두면 상호 평가로 넓힐 때 제약을 바꾸지 않아도 된다.
                @UniqueConstraint(
                        name = "uk_used_review_product_reviewer",
                        columnNames = {"used_product_id", "reviewer_account_id"})
        },
        indexes = {
                // 판매자별 후기 목록 — 후기는 상품을 거쳐 판매자에 매달리므로 조인 기준 컬럼을 잡아둔다.
                @Index(name = "idx_used_review_product", columnList = "used_product_id, created_at"),
                // 내가 쓴 후기 목록
                @Index(name = "idx_used_review_reviewer", columnList = "reviewer_account_id, created_at")
        }
)
public class UsedReview extends BaseEntity {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "used_review_id")
    private Long usedReviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_product_id", nullable = false)
    private UsedProduct usedProduct;

    // 작성자(구매자). 판매자는 usedProduct.seller로 결정되므로 따로 두지 않는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_account_id", nullable = false)
    private Account reviewer;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public static UsedReview create(
            UsedProduct usedProduct,
            Account reviewer,
            int rating,
            String content
    ) {
        validateRating(rating);

        UsedReview review = new UsedReview();
        review.usedProduct = usedProduct;
        review.reviewer = reviewer;
        review.rating = rating;
        review.content = content;
        return review;
    }

    public void update(int rating, String content) {
        validateRating(rating);
        this.rating = rating;
        this.content = content;
    }

    public boolean isWrittenBy(Long accountId) {
        return this.reviewer.getAccountId().equals(accountId);
    }

    // HTTP 밖(스케줄러·내부 호출)에서 들어와도 같은 범위를 강제한다.
    // 요청 단계 검증(@Min/@Max)만 두면 그 경로가 비어 있다.
    private static void validateRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }
}
