package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiPlanSubscriptionRepository extends JpaRepository<AiPlanSubscription, Long> {

    // 활성 구독이 2건 이상 존재할 경우 NonUniqueResultException 방지 — 최신 구독을 우선 반환
    Optional<AiPlanSubscription> findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(Long storeId);

    List<AiPlanSubscription> findByStore_StoreIdAndActiveTrue(Long storeId);

    // 만료 스케줄러 — 만료일이 지난 활성 구독 조회
    List<AiPlanSubscription> findByActiveTrueAndExpiredAtBefore(LocalDateTime now);

    // 현재 시점 기준 실제 유효한 구독 — active=true이면서 만료일이 없거나(seed 데이터) 아직 지나지 않은 것만.
    // 만료 스케줄러가 아직 돌기 전(active=true인 채 expiredAt만 지난) 구독을 유료 플랜으로 잘못 인식하지 않기 위함.
    // Pageable로 최대 1건만 받아 NonUniqueResultException을 피한다 (findFirst와 동일한 목적).
    @Query("""
        select s from AiPlanSubscription s
        where s.store.storeId = :storeId
          and s.active = true
          and (s.expiredAt is null or s.expiredAt > :now)
        order by s.createdAt desc
    """)
    List<AiPlanSubscription> findCurrentActivePlans(
            @Param("storeId") Long storeId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
