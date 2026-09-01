package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.used.entity.QUsedProduct;
import com.eeum.eeum.domain.used.entity.QUsedReview;
import com.eeum.eeum.domain.used.entity.UsedReview;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UsedReviewRepositoryImpl implements UsedReviewRepositoryCustom {

    /**
     * 후기 목록 정렬 — 작성 최신순, 동률은 PK로 끊는다.
     *
     * <p>tie-break가 없으면 createdAt 동률에서 페이지 경계 항목이 중복되거나 유실된다.
     *
     * <p>이 값 하나가 실제 SQL(orderBy)과 응답 메타데이터(Slice의 Pageable) 양쪽의 근거다.
     * 두 곳에서 따로 정하면 갈린다.
     */
    private static final Sort REVIEW_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("usedReviewId"));

    private final JPAQueryFactory queryFactory;

    private final QUsedReview review = QUsedReview.usedReview;
    private final QUsedProduct product = QUsedProduct.usedProduct;
    // 후기 한 건이 판매자와 작성자 두 계정을 참조한다 — 같은 테이블을 두 번 조인하므로 별칭을 나눈다.
    private final QAccount seller = new QAccount("seller");
    private final QAccount reviewer = new QAccount("reviewer");

    /**
     * 판매자가 받은 후기. 비회원도 볼 수 있는 판매자 평판이다.
     *
     * <p>게시글의 삭제·숨김 여부로 거르지 않는다 — 거르면 판매자가 나쁜 후기가 달린 글을 지워
     * 평판을 세탁할 수 있다. 비공개 게시글의 제목 노출은 응답 단계에서 가린다.
     */
    @Override
    public Slice<UsedReview> findSellerReviews(Long sellerId, Pageable pageable) {
        return toSlice(baseQuery().where(seller.accountId.eq(sellerId)), pageable);
    }

    // 내가 쓴 후기. 게시글마다 판매자가 다르므로 판매자 fetch join이 특히 중요하다.
    @Override
    public Slice<UsedReview> findMyReviews(Long reviewerId, Pageable pageable) {
        return toSlice(baseQuery().where(reviewer.accountId.eq(reviewerId)), pageable);
    }

    /**
     * 응답이 게시글 제목·작성자 닉네임을 담고, 공개 여부 판정({@code isPubliclyVisible})이
     * 판매자 상태까지 본다. 셋 다 fetch join해야 한다 — 하나라도 빠지면 페이지 크기만큼
     * 추가 select가 나간다(N+1).
     */
    private JPAQuery<UsedReview> baseQuery() {
        return queryFactory
                .selectFrom(review)
                .join(review.usedProduct, product).fetchJoin()
                .join(product.seller, seller).fetchJoin()
                .join(review.reviewer, reviewer).fetchJoin();
    }

    @Override
    public UsedReviewSummary aggregateSellerReviews(Long sellerId) {
        // fetch join 없이 집계만 한다 — 여기서는 행 내용이 필요 없다.
        UsedReviewSummary summary = queryFactory
                .select(Projections.constructor(UsedReviewSummary.class,
                        review.count(), review.rating.avg()))
                .from(review)
                .join(review.usedProduct, product)
                .where(product.seller.accountId.eq(sellerId))
                .fetchOne();

        // 집계 쿼리는 행이 없어도 한 줄을 돌려주지만, 방어적으로 빈 값을 만들어 둔다.
        return summary != null ? summary : new UsedReviewSummary(0L, null);
    }

    private Slice<UsedReview> toSlice(JPAQuery<UsedReview> query, Pageable pageable) {
        int size = pageable.getPageSize();
        List<UsedReview> content = query
                .orderBy(review.createdAt.desc(), review.usedReviewId.desc())
                .offset(pageable.getOffset())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }
        // 요청 sort가 아니라 실제로 적용한 정렬을 실어 보낸다.
        return new SliceImpl<>(
                content, PageRequest.of(pageable.getPageNumber(), size, REVIEW_SORT), hasNext);
    }
}
