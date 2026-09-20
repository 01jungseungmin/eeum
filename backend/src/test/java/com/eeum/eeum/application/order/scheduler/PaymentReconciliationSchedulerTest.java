package com.eeum.eeum.application.order.scheduler;

import com.eeum.eeum.application.order.service.PaymentService;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentReconciliationScheduler scheduler;

    @Test
    void 오래된_PENDING_결제는_20건까지만_대사한다() {
        Payment first = payment(1L);
        Payment second = payment(2L);
        when(paymentRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAscPaymentIdAsc(
                eq(PaymentStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        scheduler.reconcilePendingPayments();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByStatusAndCreatedAtBeforeOrderByCreatedAtAscPaymentIdAsc(
                eq(PaymentStatus.PENDING), any(LocalDateTime.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        verify(paymentService).reconcilePendingPayment(1L);
        verify(paymentService).reconcilePendingPayment(2L);
    }

    private Payment payment(Long paymentId) {
        Payment payment = mock(Payment.class);
        when(payment.getPaymentId()).thenReturn(paymentId);
        return payment;
    }
}
