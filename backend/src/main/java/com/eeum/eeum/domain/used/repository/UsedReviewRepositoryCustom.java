package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedReview;
import com.eeum.eeum.common.dto.response.CursorSlice;

/**
 * 후기 목록 조회.
 *
 * <p>정렬은 요청과 무관하게 구현이 고정한다(작성 최신순 + PK tie-break). 반환하는
 * {@link CursorSlice}의 sort에 그 정렬을 실어 보내므로 응답 메타데이터와 실제 SQL이 갈리지 않는다.
 */
public interface UsedReviewRepositoryCustom {

    /**
     * @param cursor 직전 페이지의 마지막 행. 첫 페이지면 null이다.
     * @param size   한 페이지 크기. 다음 페이지 여부 판정을 위해 내부적으로 한 건 더 읽는다.
     */
    CursorSlice<UsedReview> findSellerReviews(Long sellerId, UsedReviewCursor cursor, int size);

    CursorSlice<UsedReview> findMyReviews(Long reviewerId, UsedReviewCursor cursor, int size);

    /**
     * 판매자 평판 집계 — 후기 수와 평균 별점.
     *
     * <p>게시글의 삭제·숨김 여부로 거르지 않는다. 후기 <b>목록</b>이 그것들을 포함하는 이유가
     * "판매자가 나쁜 후기 달린 글을 지워 평판을 세탁"하는 것을 막기 위해서인데,
     * 집계만 제외하면 그 구멍이 그대로 열린다 — 지운 글의 별 1점이 평균에서 빠진다.
     */
    UsedReviewSummary aggregateSellerReviews(Long sellerId);
}
