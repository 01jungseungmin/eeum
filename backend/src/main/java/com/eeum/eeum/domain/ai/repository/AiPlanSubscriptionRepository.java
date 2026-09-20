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

    Optional<AiPlanSubscription> findByPayment_AiPlanPaymentId(Long aiPlanPaymentId);

    // 만료 스케줄러 — 만료일이 지난 활성 구독 조회
    List<AiPlanSubscription> findByActiveTrueAndExpiredAtBeforeOrderByExpiredAtAscAiPlanSubscriptionIdAsc(
            LocalDateTime now, Pageable pageable);

    // 만료 스케줄러 — 시작 시각이 지난 예약 구독. 비활성 처리된 과거 구독은 expiredAt이
    // 비활성 시각으로 당겨지므로 expiredAt 조건에서 걸러진다
    List<AiPlanSubscription> findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfterOrderByStartedAtAscAiPlanSubscriptionIdAsc(
            LocalDateTime startedAt, LocalDateTime expiredAt, Pageable pageable);

    // 현재 시점 기준 실제 유효한 구독 — 만료일이 없거나(seed 데이터) 아직 지나지 않은 것만.
    // 만료 스케줄러가 아직 돌기 전(active=true인 채 expiredAt만 지난) 구독을 유료 플랜으로 잘못 인식하지 않기 위함.
    // 시작 시각이 지난 예약 구독(active=false)도 포함한다 — 활성화는 하루 한 번 도는 스케줄러가
    // 하므로, 그 전까지 이미 결제한 하위 플랜 권한이 비는 것을 막는다. 취소·교체로 비활성화된
    // 구독은 expiredAt이 그 시각으로 당겨져 위 조건에서 걸러진다.
    // Pageable로 최대 1건만 받아 NonUniqueResultException을 피한다 (findFirst와 동일한 목적).
    @Query("""
        select s from AiPlanSubscription s
        where s.store.storeId = :storeId
          and (s.active = true or s.startedAt <= :now)
          and (s.expiredAt is null or s.expiredAt > :now)
        order by s.createdAt desc
    """)
    List<AiPlanSubscription> findCurrentActivePlans(
            @Param("storeId") Long storeId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
