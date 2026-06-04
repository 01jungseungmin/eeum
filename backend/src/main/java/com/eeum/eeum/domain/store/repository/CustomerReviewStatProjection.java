package com.eeum.eeum.domain.store.repository;

/**
 * 사장용 찜 고객 목록에서 고객별 리뷰 평점을 배치 조회할 때 사용하는 프로젝션.
 */
public interface CustomerReviewStatProjection {
    Long getAccountId();
    Double getAvgRating();
}
