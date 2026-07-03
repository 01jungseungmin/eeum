package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSavingPlanRepository extends JpaRepository<AiSavingPlan, Long> {

    boolean existsByStore_StoreIdAndStatus(Long storeId, AiSavingPlanStatus status);

    Optional<AiSavingPlan> findFirstByStore_StoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<AiSavingPlan> findByAiSavingPlanIdAndStore_StoreId(Long aiSavingPlanId, Long storeId);
}
