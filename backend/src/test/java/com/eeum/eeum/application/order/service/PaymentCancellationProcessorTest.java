package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.application.settlement.service.SettlementFeePolicy;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 고객 취소가 거래완료 주문의 결제·재고 상태를 되돌리지 않게 고정한다.
 *
 * 완료 주문을 prepare에서 거절해 PG 취소 작업 생성 이전에 상태 전이를 차단한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCancellationProcessorTest {

    private PaymentCancellationProcessor processor;

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentCancellationOperationRepository cancellationOperationRepository;
    @Mock private OwnerRevenueRepository ownerRevenueRepository;
    @Mock private WeeklySettlementRepository weeklySettlementRepository;
    @Mock private OwnerRevenueService ownerRevenueService;
    @Mock private OrderService orderService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SettlementFeePolicy settlementFeePolicy;

    @BeforeEach
    void setUp() {
        processor = new PaymentCancellationProcessor(
                orderRepository, paymentRepository, cancellationOperationRepository,
                ownerRevenueRepository, weeklySettlementRepository,
                ownerRevenueService, orderService, eventPublisher, settlementFeePolicy);
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

    @Test
    void 고객은_거래완료_주문을_직접_취소할_수_없다() {
        // 거래완료 주문은 고객 취소 분기가 상태를 확인하지 않아 PG 취소·재고 복원까지 진행할 수 있었다.
        // 완료 뒤 취소는 환불 승인 절차만 허용해야 하므로 외부 취소 작업을 만들기 전에 거절한다.
        Long orderId = 2L;
        Order order = org.mockito.Mockito.mock(Order.class);
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(orderRepository.findByIdWithPessimisticLock(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(orderId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> processor.prepare(orderId, PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);

        verifyNoInteractions(ownerRevenueService, orderService, eventPublisher);
        verify(cancellationOperationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
