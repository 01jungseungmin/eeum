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

public interface OwnerRevenueRepository extends JpaRepository<OwnerRevenue, Long> {

    boolean existsByOrder_OrderId(Long orderId);

    Optional<OwnerRevenue> findByOrder_OrderId(Long orderId);

    List<OwnerRevenue> findByStatusAndSettleableAtBeforeOrderByOwnerRevenueIdAsc(
            OwnerRevenueStatus status,
            LocalDateTime settleableAt
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
        WHERE r.order.orderId = :orderId
    """)
    Optional<OwnerRevenue> findByOrderIdWithPessimisticLock(@Param("orderId") Long orderId);
}
