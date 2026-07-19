package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 매일 03:30 — 만료일이 지난 AI 플랜 구독 비활성화 (이후 플랜 조회는 FREE 처리)
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPlanExpirationScheduler {

    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;

    @Transactional
    @Scheduled(cron = "0 30 3 * * *")
    public void expireSubscriptions() {
        List<AiPlanSubscription> expired =
                aiPlanSubscriptionRepository.findByActiveTrueAndExpiredAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        expired.forEach(AiPlanSubscription::expire);
        log.info("[AI-PLAN] 만료 구독 {}건 비활성화", expired.size());
    }
}
