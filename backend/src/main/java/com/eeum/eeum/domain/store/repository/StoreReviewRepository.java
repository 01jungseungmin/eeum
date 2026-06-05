package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.CustomerReviewStatProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreReviewRepository extends JpaRepository<StoreReview, Long> {

    //상점 리뷰 목록 조회 (최신순)
    Page<StoreReview> findByStore_StoreIdOrderByCreatedAtDesc(
            Long storeId,
            Pageable pageable
    );

    //평점 재계산용 — 해당 상점의 전체 리뷰 목록
    List<StoreReview> findByStore_StoreId(Long storeId);

    //상점 리뷰 단건 조회
    Optional<StoreReview> findByStorereviewIdAndStore_StoreId(
            Long storereviewId,
            Long storeId
    );

    //특정 주문에 대한 리뷰 존재 여부 — 1주문 1리뷰 보장용
    boolean existsByOrder_OrderId(Long orderId);

    //사용자가 작성한 리뷰 목록 (마이페이지용)
    Page<StoreReview> findByAccount_AccountIdOrderByCreatedAtDesc(
            Long accountId,
            Pageable pageable
    );

    /**
     * 사장용 찜 고객 목록 — 고객별 평균 평점 배치 조회.
     * IN절 한 번으로 N+1 없이 처리한다.
     */
    @Query("""
        SELECT r.account.accountId AS accountId,
               AVG(r.rating)       AS avgRating
        FROM StoreReview r
        WHERE r.store.storeId     = :storeId
          AND r.account.accountId IN :accountIds
        GROUP BY r.account.accountId
    """)
    List<CustomerReviewStatProjection> findReviewStatsByStoreAndAccounts(
            @Param("storeId") Long storeId,
            @Param("accountIds") List<Long> accountIds
    );

    @Query("""
    select coalesce(avg(r.rating), 0)
    from StoreReview r
    where r.store.storeId = :storeId
""")
    double calculateAverageRating(@Param("storeId") Long storeId);

    @Query("""
    select count(r)
    from StoreReview r
    where r.store.storeId = :storeId
""")
    long countByStoreId(@Param("storeId") Long storeId);
}