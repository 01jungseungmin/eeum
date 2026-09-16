package com.eeum.eeum.application.ai.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.application.ai.service.AiPlanPaymentFailureRecorder;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 결제 요청만 생성되고 PortOne 결제창을 이탈/포기해 영영 PENDING으로 남는 AI 플랜 결제 정리
// (일반 주문의 OrderExpirationScheduler와 동일한 15분 윈도우를 사용)
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPlanPaymentExpirationScheduler {

    private static final int PAYMENT_PENDING_EXPIRE_MINUTES = 15;
    private static final Duration PAYMENT_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiPlanPaymentRepository aiPlanPaymentRepository;
    private final AiPlanPaymentFailureRecorder failureRecorder;
    private final RedisLockService redisLockService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "expirePendingPayments", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void expirePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PAYMENT_PENDING_EXPIRE_MINUTES);
        List<AiPlanPayment> pendingPayments =
                aiPlanPaymentRepository.findByStatusAndCreatedAtBefore(AiPlanPaymentStatus.PENDING, threshold);

        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("[AI-PLAN-PAYMENT-EXPIRE] 결제 대기 만료 대상: {}건", pendingPayments.size());

        for (AiPlanPayment payment : pendingPayments) {
            String paymentId = payment.getPortonePaymentId();
            try {
                // 결제 완료 경로(AiPlanSubscriptionService)와 동일한 락으로 직렬화 — 만료 처리와 결제 완료가
                // 동시에 진행돼 PAID가 FAILED로 덮이는 경합을 막는다(markFailed의 PENDING 가드와 이중 방어).
                redisLockService.executeWithLock(
                        LockKeys.aiPlanPayment(paymentId),
                        PAYMENT_LOCK_LEASE,
                        ErrorCode.LOCK_PAYMENT_FAILED,
                        () -> failureRecorder.markFailed(paymentId));
            } catch (Exception e) {
                log.warn("[AI-PLAN-PAYMENT-EXPIRE] 결제 대기 만료 처리 실패: paymentId={}", paymentId, e);
            }
        }
    }
}
