package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiPlanSubscriptionService;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanPaymentCancellationOperation;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentCancellationOperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

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
        when(cancellationOperationRepository.findCandidatesByStatusModifiedBefore(
                eq(AiPlanPaymentCancellationStatus.PENDING), any(LocalDateTime.class), any(), any(), any()))
                .thenReturn(List.of(candidate("ai-plan-pending")), List.of());
        when(cancellationOperationRepository.findCandidatesByStatusModifiedBefore(
                eq(AiPlanPaymentCancellationStatus.REQUESTED), any(LocalDateTime.class), any(), any(), any()))
                .thenReturn(List.of(candidate("ai-plan-requested")), List.of());

        scheduler.reconcileCancellations();

        verify(aiPlanSubscriptionService).retryPendingMismatchedPaymentCancellation("ai-plan-pending");
        verify(aiPlanSubscriptionService).reconcileRequestedMismatchedPaymentCancellation("ai-plan-requested");
    }

    private AiPlanPaymentCancellationOperation candidate(String paymentId) {
        AiPlanPayment payment = org.mockito.Mockito.mock(AiPlanPayment.class);
        when(payment.getPortonePaymentId()).thenReturn(paymentId);
        AiPlanPaymentCancellationOperation operation = org.mockito.Mockito.mock(AiPlanPaymentCancellationOperation.class);
        when(operation.getPayment()).thenReturn(payment);
        when(operation.getId()).thenReturn(1L);
        when(operation.getModifiedAt()).thenReturn(LocalDateTime.now().minusMinutes(10));
        return operation;
    }
}
