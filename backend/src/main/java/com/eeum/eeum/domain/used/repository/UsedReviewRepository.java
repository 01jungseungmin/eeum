package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsedReviewRepository
        extends JpaRepository<UsedReview, Long>, UsedReviewRepositoryCustom {

    // 중복 작성 사전 확인. 최종 방어는 uk_used_review_product_reviewer다 —
    // 존재 확인과 INSERT 사이에 다른 요청이 끼어들 수 있어 이 확인만으로는 부족하다.
    boolean existsByUsedProduct_UsedProductIdAndReviewer_AccountId(
            Long usedProductId, Long reviewerAccountId);

    /**
     * 수정·삭제 대상 조회. 소유권까지 한 번에 좁혀 남의 후기 ID로 접근하는 경로를 없앤다.
     *
     * <p>잠금 조회다. 같은 사용자가 수정과 삭제를 동시에 보내면(재시도·두 기기) 일반 조회로는
     * 삭제가 먼저 커밋된 뒤 수정이 flush에서 StaleStateException으로 끝난다 — 사용자에게는 500이다.
     * 잠근 뒤 읽으면 뒤에 온 요청이 사라진 행을 보고 깨끗한 404로 끝난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM UsedReview r
        WHERE r.usedReviewId = :usedReviewId AND r.reviewer.accountId = :reviewerAccountId
        """)
    Optional<UsedReview> findForUpdateByIdAndReviewer(
            @Param("usedReviewId") Long usedReviewId,
            @Param("reviewerAccountId") Long reviewerAccountId);
}
