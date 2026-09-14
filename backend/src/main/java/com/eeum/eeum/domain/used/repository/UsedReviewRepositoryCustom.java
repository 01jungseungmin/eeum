package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedReview;
import com.eeum.eeum.common.dto.response.CursorSlice;

/** 후기 목록과 판매자 평판을 조회한다. 정렬은 최신순과 PK tie-break로 고정한다. */
public interface UsedReviewRepositoryCustom {

    /**
     * @param cursor 직전 페이지의 마지막 행. 첫 페이지면 null이다.
     * @param size   한 페이지 크기. 다음 페이지 여부 판정을 위해 내부적으로 한 건 더 읽는다.
     */
    CursorSlice<UsedReview> findSellerReviews(Long sellerId, UsedReviewCursor cursor, int size);

    CursorSlice<UsedReview> findMyReviews(Long reviewerId, UsedReviewCursor cursor, int size);

    /** 삭제·숨김 게시글의 후기도 평판 세탁 방지를 위해 집계한다. */
    UsedReviewSummary aggregateSellerReviews(Long sellerId);
}
