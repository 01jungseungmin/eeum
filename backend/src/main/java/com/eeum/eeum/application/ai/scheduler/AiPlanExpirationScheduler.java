package com.eeum.eeum.application.ai.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
    @SchedulerLock(name = "expireSubscriptions", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void expireSubscriptions() {
        LocalDateTime now = LocalDateTime.now();

        List<AiPlanSubscription> expired = aiPlanSubscriptionRepository.findByActiveTrueAndExpiredAtBefore(now);
        expired.forEach(AiPlanSubscription::expire);
        if (!expired.isEmpty()) {
            log.info("[AI-PLAN] 만료 구독 {}건 비활성화", expired.size());
        }

        // 하위 플랜으로 바꾼 결제는 상위 기간이 끝난 뒤부터 시작하도록 예약돼 있다.
        List<AiPlanSubscription> reserved = aiPlanSubscriptionRepository
                .findByActiveFalseAndStartedAtLessThanEqualAndExpiredAtAfter(now, now);
        reserved.forEach(AiPlanSubscription::activate);
        if (!reserved.isEmpty()) {
            log.info("[AI-PLAN] 예약 구독 {}건 활성화", reserved.size());
        }
    }
}
