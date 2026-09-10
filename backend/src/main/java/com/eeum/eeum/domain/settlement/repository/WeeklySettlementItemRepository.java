package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklySettlementItemRepository extends JpaRepository<WeeklySettlementItem, Long> {

    Optional<WeeklySettlementItem> findByOwnerRevenue_OwnerRevenueId(Long ownerRevenueId);

    @Query("""
        SELECT i
        FROM WeeklySettlementItem i
        WHERE i.weeklySettlement.weeklySettlementId = :weeklySettlementId
        ORDER BY i.ownerRevenue.ownerRevenueId ASC
    """)
    List<WeeklySettlementItem> findByWeeklySettlementIdOrderByOwnerRevenueId(
            @Param("weeklySettlementId") Long weeklySettlementId);
}
