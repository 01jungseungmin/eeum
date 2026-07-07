package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiPlanSubscriptionRepository extends JpaRepository<AiPlanSubscription, Long> {

    // 활성 구독이 2건 이상 존재할 경우 NonUniqueResultException 방지 — 최신 구독을 우선 반환
    Optional<AiPlanSubscription> findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(Long storeId);

    List<AiPlanSubscription> findByStore_StoreIdAndActiveTrue(Long storeId);

    // 만료 스케줄러 — 만료일이 지난 활성 구독 조회
    List<AiPlanSubscription> findByActiveTrueAndExpiredAtBefore(LocalDateTime now);
}
