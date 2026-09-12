package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeeklySettlementItemRepository extends JpaRepository<WeeklySettlementItem, Long> {

    Optional<WeeklySettlementItem> findByOwnerRevenue_OwnerRevenueId(Long ownerRevenueId);

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

    /**
     * 포함 항목의 금액 합계. 항목이 하나도 없으면 SUM이 null이라 결과 필드도 null이므로,
     * 호출 전에 항목 존재를 먼저 확인한다.
     */
    @Query("""
        SELECT new com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemAmounts(
            SUM(i.paymentAmount), SUM(i.pgFeeAmount), SUM(i.platformFeeAmount), SUM(i.payoutAmount))
        FROM WeeklySettlementItem i
        WHERE i.weeklySettlement.weeklySettlementId = :weeklySettlementId
    """)
    WeeklySettlementItemAmounts sumAmountsByWeeklySettlementId(
            @Param("weeklySettlementId") Long weeklySettlementId);
}
