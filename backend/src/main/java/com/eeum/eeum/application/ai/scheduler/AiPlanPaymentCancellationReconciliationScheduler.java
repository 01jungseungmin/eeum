package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiPlanSubscriptionService;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;
import com.eeum.eeum.domain.ai.entity.AiPlanPaymentCancellationOperation;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentCancellationOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** PortOne Webhook이 유실돼도 AI 금액 불일치 환불 작업을 회수한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPlanPaymentCancellationReconciliationScheduler {

    private static final int RECOVERY_DELAY_MINUTES = 5;
    private static final int RECOVERY_BATCH_SIZE = 100;
    private static final int RECOVERY_MAX_PER_RUN = 500;

    private final AiPlanPaymentCancellationOperationRepository cancellationOperationRepository;
    private final AiPlanSubscriptionService aiPlanSubscriptionService;

    @Scheduled(fixedDelay = 300_000)
    @SchedulerLock(name = "reconcileAiPlanPaymentCancellations", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void reconcileCancellations() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RECOVERY_DELAY_MINUTES);
        reconcile(AiPlanPaymentCancellationStatus.PENDING, threshold, this::retryPending);
        reconcile(AiPlanPaymentCancellationStatus.REQUESTED, threshold, this::reconcileRequested);
    }

    private void reconcile(
            AiPlanPaymentCancellationStatus status, LocalDateTime threshold, Consumer<String> action
    ) {
        int remaining = RECOVERY_MAX_PER_RUN;
        while (remaining > 0) {
            int size = Math.min(RECOVERY_BATCH_SIZE, remaining);
            List<AiPlanPaymentCancellationOperation> candidates = cancellationOperationRepository
                    .findCandidatesByStatusModifiedBefore(
                            status, threshold, null, null, PageRequest.of(0, size));
            if (candidates.isEmpty()) {
                return;
            }

            candidates.forEach(candidate -> action.accept(candidate.getPayment().getPortonePaymentId()));
            remaining -= candidates.size();
            if (candidates.size() < size) {
                return;
            }
        }
    }

    private void retryPending(String paymentId) {
        try {
            aiPlanSubscriptionService.retryPendingMismatchedPaymentCancellation(paymentId);
        } catch (RuntimeException e) {
            log.warn("[AI-PLAN] 대기 환불 재시도 실패: paymentId={}", paymentId, e);
        }
    }

    private void reconcileRequested(String paymentId) {
        try {
            aiPlanSubscriptionService.reconcileRequestedMismatchedPaymentCancellation(paymentId);
        } catch (RuntimeException e) {
            log.warn("[AI-PLAN] 접수 환불 대사 실패: paymentId={}", paymentId, e);
        }
    }
}
