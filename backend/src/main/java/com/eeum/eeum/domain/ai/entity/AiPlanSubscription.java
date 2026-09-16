package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_plan_subscription")
// 가게별 AI 플랜 구독 정보
public class AiPlanSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_plan_subscription_id")
    private Long aiPlanSubscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private AiPlanType planType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    public static AiPlanSubscription create(Store store, AiPlanType planType, LocalDateTime startedAt) {
        AiPlanSubscription subscription = new AiPlanSubscription();
        subscription.store = store;
        subscription.planType = planType;
        subscription.startedAt = startedAt;
        subscription.active = true;
        return subscription;
    }

    // 결제 기반 구독 — 결제 기간 종료일(expiredAt)을 함께 저장, 만료 스케줄러가 비활성화 처리
    public static AiPlanSubscription createWithPeriod(
            Store store, AiPlanType planType, LocalDateTime startedAt, LocalDateTime expiredAt) {
        AiPlanSubscription subscription = create(store, planType, startedAt);
        subscription.expiredAt = expiredAt;
        return subscription;
    }

    public void deactivate(LocalDateTime expiredAt) {
        this.active = false;
        this.expiredAt = expiredAt;
    }

    // 만료일 도래 시 비활성화 (expiredAt은 유지)
    public void expire() {
        this.active = false;
    }
}
