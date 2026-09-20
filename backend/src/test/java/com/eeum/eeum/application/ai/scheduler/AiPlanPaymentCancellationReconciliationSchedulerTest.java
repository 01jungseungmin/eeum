package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiPlanSubscriptionService;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentCancellationOperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanPaymentCancellationReconciliationSchedulerTest {

    @Mock private AiPlanPaymentCancellationOperationRepository cancellationOperationRepository;
    @Mock private AiPlanSubscriptionService aiPlanSubscriptionService;

    @InjectMocks private AiPlanPaymentCancellationReconciliationScheduler scheduler;

    @Test
    void 대기_환불은_재요청하고_접수된_환불은_PortOne_대사로_회수한다() {
        when(cancellationOperationRepository.findPaymentIdsByStatusModifiedBefore(
                eq(AiPlanPaymentCancellationStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of("ai-plan-pending"));
        when(cancellationOperationRepository.findPaymentIdsByStatusModifiedBefore(
                eq(AiPlanPaymentCancellationStatus.REQUESTED), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of("ai-plan-requested"));

        scheduler.reconcileCancellations();

        verify(aiPlanSubscriptionService).retryPendingMismatchedPaymentCancellation("ai-plan-pending");
        verify(aiPlanSubscriptionService).reconcileRequestedMismatchedPaymentCancellation("ai-plan-requested");
    }
}
