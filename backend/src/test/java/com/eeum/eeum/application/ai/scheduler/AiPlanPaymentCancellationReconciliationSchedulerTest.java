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
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanPaymentCancellationReconciliationSchedulerTest {

    @Mock private AiPlanPaymentCancellationOperationRepository cancellationOperationRepository;
    @Mock private AiPlanSubscriptionService aiPlanSubscriptionService;

    @InjectMocks private AiPlanPaymentCancellationReconciliationScheduler scheduler;

    @Test
    void 대기_환불은_재요청하고_접수된_환불은_PortOne_대사로_회수한다() {
        // candidate()가 내부에서 when()을 호출하므로 바깥 when()의 인자 자리에서 만들면
        // 스터빙이 겹쳐 UnfinishedStubbingException이 난다. 먼저 만들어 둔다.
        AiPlanPaymentCancellationOperation pending = candidate("ai-plan-pending");
        AiPlanPaymentCancellationOperation requested = candidate("ai-plan-requested");

        when(cancellationOperationRepository.findCandidatesByStatusModifiedBefore(
                eq(AiPlanPaymentCancellationStatus.PENDING), any(LocalDateTime.class), any(), any(), any()))
                .thenReturn(List.of(pending));
        when(cancellationOperationRepository.findCandidatesByStatusModifiedBefore(
                eq(AiPlanPaymentCancellationStatus.REQUESTED), any(LocalDateTime.class), any(), any(), any()))
                .thenReturn(List.of(requested));

        scheduler.reconcileCancellations();

        verify(aiPlanSubscriptionService).retryPendingMismatchedPaymentCancellation("ai-plan-pending");
        verify(aiPlanSubscriptionService).reconcileRequestedMismatchedPaymentCancellation("ai-plan-requested");

        org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(cancellationOperationRepository, times(2)).findCandidatesByStatusModifiedBefore(
                any(), any(LocalDateTime.class), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getAllValues()).allSatisfy(pageable ->
                assertThat(pageable.getPageSize()).isEqualTo(10));
    }

    private AiPlanPaymentCancellationOperation candidate(String paymentId) {
        AiPlanPayment payment = org.mockito.Mockito.mock(AiPlanPayment.class);
        when(payment.getPortonePaymentId()).thenReturn(paymentId);
        AiPlanPaymentCancellationOperation operation = org.mockito.Mockito.mock(AiPlanPaymentCancellationOperation.class);
        // 스케줄러는 커서 인자에 null을 넘겨 첫 페이지만 읽으므로 getId()/getModifiedAt()은
        // 호출되지 않는다. 커서 페이징을 실제로 쓰게 되면 그때 스터빙을 되살린다.
        when(operation.getPayment()).thenReturn(payment);
        return operation;
    }
}
