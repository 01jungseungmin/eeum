package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiPlanSubscriptionRepository extends JpaRepository<AiPlanSubscription, Long> {

    Optional<AiPlanSubscription> findByStore_StoreIdAndActiveTrue(Long storeId);
}
