package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 거래완료의 DB 단계. 주문 락은 {@link StoreOrderService}가 잡고 여기서는 잠근 뒤의
 * 상태 검증과 원장 연결만 본다.
 */
@ExtendWith(MockitoExtension.class)
class StoreOrderCompletionProcessorTest {

    private static final Long ORDER_ID = 1L;
    private static final Long OWNER_ID = 200L;
    private static final Long CUSTOMER_ID = 300L;

    @InjectMocks private StoreOrderCompletionProcessor processor;

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OwnerRevenueService ownerRevenueService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void 온라인결제_주문은_거래완료되고_수익_원장에_연결된다() {
        // given
        Order order = order(OrderStatus.READY);
        Payment payment = payment(order, PaymentMethod.CARD, PaymentStatus.PAID);
        givenLocked(order, payment);

        // when
        processor.complete(OWNER_ID, ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(ownerRevenueService).recordPaidOrder(order, payment);
        verify(ownerRevenueService).markOrderCompleted(order, payment);
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 현장결제_주문도_거래완료되며_원장_판단은_정산에_위임한다() {
        // given — 현금은 사장이 직접 받았다. 지급 대상인지는 OwnerRevenueService가 정한다.
        Order order = order(OrderStatus.READY);
        Payment payment = payment(order, PaymentMethod.CASH_ON_SITE, PaymentStatus.NOT_PAID);
        givenLocked(order, payment);

        // when
        processor.complete(OWNER_ID, ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(ownerRevenueService).recordPaidOrder(order, payment);
        verify(ownerRevenueService).markOrderCompleted(order, payment);
    }

    @Test
    void 주문이_없으면_ORDER_NOT_FOUND() {
        // given
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> processor.complete(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 상점주가_아니면_STORE_ACCESS_DENIED() {
        // given
        Order order = order(OrderStatus.READY);
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));

        // when / then
        assertThatThrownBy(() -> processor.complete(999L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);

        verify(paymentRepository, never()).findByOrderIdWithPessimisticLock(any());
    }

    @Test
    void 취소가_먼저_커밋된_주문은_거래완료로_되살릴_수_없다() {
        // given — 잠근 뒤 다시 읽은 주문이 이미 CANCELLED다
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));

        // when / then
        assertThatThrownBy(() -> processor.complete(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);

        // 환불된 주문에 지급 원장이 생기면 안 된다
        verify(ownerRevenueService, never()).recordPaidOrder(any(), any());
        verify(paymentRepository, never()).findByOrderIdWithPessimisticLock(any());
    }

    @Test
    void 상태가_READY가_아니면_ORDER_INVALID_STATUS() {
        // given
        Order order = order(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));

        // when / then
        assertThatThrownBy(() -> processor.complete(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
    }

    @Test
    void 결제정보가_없으면_PAYMENT_NOT_FOUND() {
        // given
        Order order = order(OrderStatus.READY);
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> processor.complete(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 현장결제가_이미_결제됐으면_ORDER_INVALID_STATUS() {
        // given
        Order order = order(OrderStatus.READY);
        givenLocked(order, payment(order, PaymentMethod.CASH_ON_SITE, PaymentStatus.PAID));

        // when / then
        assertThatThrownBy(() -> processor.complete(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
    }

    @Test
    void 온라인결제가_PAID가_아니면_PAYMENT_NOT_COMPLETED() {
        // given
        Order order = order(OrderStatus.READY);
        givenLocked(order, payment(order, PaymentMethod.CARD, PaymentStatus.PENDING));

        // when / then
        assertThatThrownBy(() -> processor.complete(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_COMPLETED);

        verify(ownerRevenueService, never()).recordPaidOrder(any(), any());
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void givenLocked(Order order, Payment payment) {
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(payment));
    }

    private Order order(OrderStatus status) {
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = Store.createForOwnerSignup(owner, "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", 1L);

        Order order = Order.create(customer, store, BigDecimal.valueOf(10000),
                "ORD-0001", OrderType.SALE, null, null);
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private Payment payment(Order order, PaymentMethod method, PaymentStatus status) {
        Payment payment = Payment.create(order, mock(Account.class), "portone-1", "idem-1",
                BigDecimal.valueOf(10000), method, status);
        ReflectionTestUtils.setField(payment, "paymentId", 1L);
        return payment;
    }
}
