package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationProcessorTest {

    private PaymentCancellationProcessor processor;

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentCancellationOperationRepository cancellationOperationRepository;
    @Mock private OwnerRevenueService ownerRevenueService;
    @Mock private OrderService orderService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        processor = new PaymentCancellationProcessor(
                orderRepository, paymentRepository, cancellationOperationRepository,
                ownerRevenueService, orderService, eventPublisher);
    }

    @Test
    void PG_취소가_확정되기_전에는_내부_취소를_반영하지_않는다() {
        // given
        Long operationId = 1L;
        Long orderId = 2L;
        PaymentCancellationOperation operation = org.mockito.Mockito.mock(PaymentCancellationOperation.class);
        when(cancellationOperationRepository.findOrderIdByOperationId(operationId)).thenReturn(Optional.of(orderId));
        when(orderRepository.findByIdWithPessimisticLock(orderId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Order.class)));
        when(paymentRepository.findByOrderIdWithPessimisticLock(orderId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Payment.class)));
        when(cancellationOperationRepository.findByIdWithPessimisticLock(operationId)).thenReturn(Optional.of(operation));
        when(operation.isPgCancelled()).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> processor.applyCancellation(
                operationId, PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);

        verifyNoInteractions(ownerRevenueService, orderService, eventPublisher);
        org.mockito.Mockito.verify(operation, never()).markCompleted(org.mockito.ArgumentMatchers.any());
    }
}
