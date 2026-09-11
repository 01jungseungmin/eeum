package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WeeklySettlementRepository extends JpaRepository<WeeklySettlement, Long> {

    @Modifying
    @Query(value = """
        INSERT INTO weekly_settlement
        (store_id, period_start_at, period_end_at, payment_amount, pg_fee_amount, platform_fee_amount,
         payout_amount, status, payout_idempotency_key, version, created_at, modified_at)
        VALUES (:storeId, :periodStartAt, :periodEndAt, 0, 0, 0, 0, 'PAYOUT_PENDING',
                :idempotencyKey, 0, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE weekly_settlement_id = weekly_settlement_id
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("storeId") Long storeId,
            @Param("periodStartAt") LocalDateTime periodStartAt,
            @Param("periodEndAt") LocalDateTime periodEndAt,
            @Param("idempotencyKey") String idempotencyKey);

    Page<WeeklySettlement> findByStore_StoreIdOrderByPeriodEndAtDesc(Long storeId, Pageable pageable);

    Page<WeeklySettlement> findAllByOrderByPeriodEndAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM WeeklySettlement s
        WHERE s.store.storeId = :storeId
          AND s.periodStartAt = :periodStartAt
          AND s.periodEndAt = :periodEndAt
    """)
    Optional<WeeklySettlement> findByStoreAndPeriodWithPessimisticLock(
            @Param("storeId") Long storeId,
            @Param("periodStartAt") LocalDateTime periodStartAt,
            @Param("periodEndAt") LocalDateTime periodEndAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM WeeklySettlement s
        WHERE s.weeklySettlementId = :weeklySettlementId
    """)
    Optional<WeeklySettlement> findByIdWithPessimisticLock(
            @Param("weeklySettlementId") Long weeklySettlementId
    );

    /**
     * 취소 경로의 첫 잠금. 항목이 있는 경우 반드시 주간 정산을 먼저 잠근 뒤 원장을 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM WeeklySettlement s
        JOIN WeeklySettlementItem i ON i.weeklySettlement = s
        WHERE i.ownerRevenue.ownerRevenueId = :ownerRevenueId
    """)
    Optional<WeeklySettlement> findByOwnerRevenueIdWithPessimisticLock(
            @Param("ownerRevenueId") Long ownerRevenueId
    );
}
