package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.used.entity.QUsedProduct;
import com.eeum.eeum.domain.used.entity.QUsedReview;
import com.eeum.eeum.domain.used.entity.UsedReview;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public Slice<UsedReview> findSellerReviews(Long sellerId, UsedReviewCursor cursor, int size) {
        return toSlice(baseQuery().where(seller.accountId.eq(sellerId)), cursor, size);
    }

    // 내가 쓴 후기. 게시글마다 판매자가 다르므로 판매자 fetch join이 특히 중요하다.
    @Override
    public Slice<UsedReview> findMyReviews(Long reviewerId, UsedReviewCursor cursor, int size) {
        return toSlice(baseQuery().where(reviewer.accountId.eq(reviewerId)), cursor, size);
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

    /**
     * 커서(keyset) 페이징. OFFSET을 쓰지 않는다.
     *
     * <p>최신순 목록은 새 행이 맨 앞에 꽂히므로 OFFSET은 페이지 사이 삽입 한 건에 통째로 밀린다 —
     * 경계 항목이 다음 페이지에서 중복으로 나오거나(삽입), 건너뛰어진다(삭제).
     * 커서는 "이 행 다음부터"를 가리켜 그 사이 변화와 무관하다.
     *
     * <p>페이지 번호는 의미가 없어 항상 0으로 채운다. 클라이언트는 받은 마지막 항목의
     * {@code createdAt}과 {@code usedReviewId}를 다음 요청의 커서로 그대로 보내면 된다.
     */
    private Slice<UsedReview> toSlice(JPAQuery<UsedReview> query, UsedReviewCursor cursor, int size) {
        List<UsedReview> content = query
                .where(afterCursor(cursor))
                .orderBy(review.createdAt.desc(), review.usedReviewId.desc())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }
        // 요청 sort가 아니라 실제로 적용한 정렬을 실어 보낸다.
        return new SliceImpl<>(content, PageRequest.of(0, size, REVIEW_SORT), hasNext);
    }

    /**
     * 커서 이후 구간. 정렬이 {@code createdAt desc, usedReviewId desc}이므로
     * "createdAt이 더 이르거나, 같으면 ID가 더 작은" 행들이다.
     *
     * <p>두 번째 항(동률에서 ID로 끊기)이 빠지면 같은 시각에 등록된 후기들이
     * 페이지 경계에서 중복되거나 누락된다 — tie-break 정렬만으로는 막을 수 없다.
     */
    private BooleanExpression afterCursor(UsedReviewCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return review.createdAt.lt(cursor.createdAt())
                .or(review.createdAt.eq(cursor.createdAt())
                        .and(review.usedReviewId.lt(cursor.usedReviewId())));
    }
}
