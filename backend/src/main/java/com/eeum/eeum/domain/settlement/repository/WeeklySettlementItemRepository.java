package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklySettlementItemRepository extends JpaRepository<WeeklySettlementItem, Long> {

    java.util.Optional<WeeklySettlementItem> findByOwnerRevenue_OwnerRevenueId(Long ownerRevenueId);

    @Query("""
        SELECT i
        FROM WeeklySettlementItem i
        WHERE i.weeklySettlement.weeklySettlementId = :weeklySettlementId
        ORDER BY i.ownerRevenue.ownerRevenueId ASC
    """)
    List<WeeklySettlementItem> findByWeeklySettlementIdOrderByOwnerRevenueId(
            @Param("weeklySettlementId") Long weeklySettlementId);

    @Query("""
        SELECT i.ownerRevenue.order.orderId
        FROM WeeklySettlementItem i
        WHERE i.weeklySettlement.weeklySettlementId = :weeklySettlementId
        ORDER BY i.ownerRevenue.ownerRevenueId ASC
    """)
    List<Long> findOrderIdsByWeeklySettlementId(@Param("weeklySettlementId") Long weeklySettlementId);

    @Query("""
        SELECT i.ownerRevenue.ownerRevenueId
        FROM WeeklySettlementItem i
        WHERE i.weeklySettlement.weeklySettlementId = :weeklySettlementId
        ORDER BY i.ownerRevenue.ownerRevenueId ASC
    """)
    List<Long> findOwnerRevenueIdsByWeeklySettlementId(@Param("weeklySettlementId") Long weeklySettlementId);
}
