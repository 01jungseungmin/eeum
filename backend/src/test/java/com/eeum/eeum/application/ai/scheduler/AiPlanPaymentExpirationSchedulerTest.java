package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiPlanPaymentFailureRecorder;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanPaymentExpirationSchedulerTest {

    @InjectMocks
    private AiPlanPaymentExpirationScheduler scheduler;

    @Mock private AiPlanPaymentRepository aiPlanPaymentRepository;
    @Mock private AiPlanPaymentFailureRecorder failureRecorder;
    @Mock private com.eeum.eeum.common.service.RedisLockService redisLockService;

    @org.junit.jupiter.api.BeforeEach
    void stubLock() {
        // markFailed를 감싸는 분산 락 — Runnable을 그대로 실행하도록 스텁
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return null;
        }).when(redisLockService).executeWithLock(
                any(String.class), any(java.time.Duration.class),
                any(com.eeum.eeum.exception.ErrorCode.class), any(Runnable.class));
    }

    @Test
    void 만료_대상이_없으면_아무것도_하지_않는다() {
        // given
        when(aiPlanPaymentRepository.findByStatusAndCreatedAtBefore(eq(AiPlanPaymentStatus.PENDING), any()))
                .thenReturn(List.of());

        // when
        scheduler.expirePendingPayments();

        // then
        verify(failureRecorder, never()).markFailed(any());
    }

    @Test
    void 만료_대상_결제는_failureRecorder에_위임해_FAILED_처리한다() {
        // given
        AiPlanPayment payment = mock(AiPlanPayment.class);
        when(payment.getPortonePaymentId()).thenReturn("ai-plan-abc123");
        when(aiPlanPaymentRepository.findByStatusAndCreatedAtBefore(eq(AiPlanPaymentStatus.PENDING), any()))
                .thenReturn(List.of(payment));

        // when
        scheduler.expirePendingPayments();

        // then
        verify(failureRecorder).markFailed("ai-plan-abc123");
    }

    @Test
    void 한_건_처리_중_예외가_발생해도_나머지_건은_계속_처리된다() {
        // given
        AiPlanPayment failing = mock(AiPlanPayment.class);
        when(failing.getPortonePaymentId()).thenReturn("ai-plan-fail");
        AiPlanPayment succeeding = mock(AiPlanPayment.class);
        when(succeeding.getPortonePaymentId()).thenReturn("ai-plan-ok");
        when(aiPlanPaymentRepository.findByStatusAndCreatedAtBefore(eq(AiPlanPaymentStatus.PENDING), any()))
                .thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("redis down")).when(failureRecorder).markFailed("ai-plan-fail");

        // when
        scheduler.expirePendingPayments();

        // then
        verify(failureRecorder).markFailed("ai-plan-ok");
    }

    @Test
    void 만료_기준시각은_15분_전이다() {
        // given
        when(aiPlanPaymentRepository.findByStatusAndCreatedAtBefore(eq(AiPlanPaymentStatus.PENDING), any()))
                .thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusMinutes(15).minusSeconds(5);

        // when
        scheduler.expirePendingPayments();

        // then — 캡처한 threshold가 "지금 - 15분" 근처인지 확인
        org.mockito.ArgumentCaptor<LocalDateTime> captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(aiPlanPaymentRepository).findByStatusAndCreatedAtBefore(eq(AiPlanPaymentStatus.PENDING), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).isAfter(before);
    }
}
