package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsedReviewRepository extends JpaRepository<UsedReview, Long> {

    // 중복 작성 사전 확인. 최종 방어는 uk_used_review_product_reviewer다 —
    // 존재 확인과 INSERT 사이에 다른 요청이 끼어들 수 있어 이 확인만으로는 부족하다.
    boolean existsByUsedProduct_UsedProductIdAndReviewer_AccountId(
            Long usedProductId, Long reviewerAccountId);

    // 수정·삭제 대상 조회. 소유권까지 한 번에 좁혀 남의 후기 ID로 접근하는 경로를 없앤다.
    Optional<UsedReview> findByUsedReviewIdAndReviewer_AccountId(
            Long usedReviewId, Long reviewerAccountId);
}
