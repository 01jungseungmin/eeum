package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 후기 목록 조회.
 *
 * <p>정렬은 요청과 무관하게 구현이 고정한다(작성 최신순 + PK tie-break). 반환하는 Slice의
 * Pageable에 그 정렬을 실어 보내므로 응답 메타데이터와 실제 SQL이 갈리지 않는다.
 */
public interface UsedReviewRepositoryCustom {

    Slice<UsedReview> findSellerReviews(Long sellerId, Pageable pageable);

    Slice<UsedReview> findMyReviews(Long reviewerId, Pageable pageable);
}
