package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface WeeklySettlementItemRepository extends JpaRepository<WeeklySettlementItem, Long> {

    Optional<WeeklySettlementItem> findByOwnerRevenue_OwnerRevenueId(Long ownerRevenueId);

    List<WeeklySettlementItem> findByWeeklySettlement_WeeklySettlementId(Long weeklySettlementId);
}
