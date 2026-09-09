package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySettlementItemRepository extends JpaRepository<WeeklySettlementItem, Long> {
}
