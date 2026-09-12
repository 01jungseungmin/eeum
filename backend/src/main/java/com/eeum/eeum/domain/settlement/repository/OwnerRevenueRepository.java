package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OwnerRevenueRepository extends JpaRepository<OwnerRevenue, Long> {

    Optional<OwnerRevenue> findByOrder_OrderId(Long orderId);

    Page<OwnerRevenue> findByStore_StoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    @Query("""
        SELECT r.ownerRevenueId
        FROM OwnerRevenue r
        WHERE r.status = :status
          AND r.settleableAt < :settleableAt
        ORDER BY r.ownerRevenueId ASC
    """)
    List<Long> findEligibleIds(
            @Param("status") OwnerRevenueStatus status,
            @Param("settleableAt") LocalDateTime settleableAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM OwnerRevenue r
        WHERE r.ownerRevenueId = :ownerRevenueId
    """)
    Optional<OwnerRevenue> findByIdWithPessimisticLock(@Param("ownerRevenueId") Long ownerRevenueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM OwnerRevenue r
        WHERE r.ownerRevenueId IN :ownerRevenueIds
        ORDER BY r.ownerRevenueId ASC
    """)
    List<OwnerRevenue> findAllByIdInWithPessimisticLock(
            @Param("ownerRevenueIds") List<Long> ownerRevenueIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM OwnerRevenue r
        WHERE r.order.orderId = :orderId
    """)
    Optional<OwnerRevenue> findByOrderIdWithPessimisticLock(@Param("orderId") Long orderId);
}
