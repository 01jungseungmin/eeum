package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
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
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreOrderServiceTest {

    @InjectMocks
    private StoreOrderService storeOrderService;

    @Mock private StoreRepository storeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RedisLockService redisLockService;
    @Mock private OrderService orderService;
    @Mock private PortOnePaymentClient portOnePaymentClient;

    private static final Long ORDER_ID = 1L;
    private static final Long OWNER_ID = 200L;
    private static final Long CUSTOMER_ID = 300L;

    // approveRefund/rejectOrder가 감싸는 분산 락 — Runnable을 그대로 실행하도록 스텁 (개별 테스트가
    // doThrow로 재정의하면 그 테스트에서는 그쪽이 우선한다)
    @org.junit.jupiter.api.BeforeEach
    void stubOrderLock() {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return null;
        }).when(redisLockService).executeWithLock(
                anyString(), any(Duration.class), any(ErrorCode.class), any(Runnable.class));
    }

    // ──────────────────── Helpers ────────────────────

    private Store createStore(Account ownerAccount) {
        Store store = Store.createForOwnerSignup(ownerAccount, "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", 1L);
        return store;
    }

    private Order createOrder(Store store, Account customerAccount, OrderStatus status) {
        Order order = Order.create(customerAccount, store, BigDecimal.valueOf(10000),
                "ORD-0001", OrderType.SALE, null, null);
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private Payment createPayment(Order order, Account customerAccount,
                                   PaymentMethod method, PaymentStatus status) {
        Payment payment = Payment.create(order, customerAccount, "portone-1", "idem-1",
                BigDecimal.valueOf(10000), method, status);
        ReflectionTestUtils.setField(payment, "paymentId", 1L);
        return payment;
    }

    // ──────────────────── readyOrder ────────────────────

    @Test
    void 주문_준비완료_처리_성공() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.CONFIRMED);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when
        storeOrderService.readyOrder(OWNER_ID, ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 주문_준비완료_처리_시_주문이_없으면_ORDER_NOT_FOUND() {
        // given
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.readyOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 주문_준비완료_처리_시_상점주가_아니면_STORE_ACCESS_DENIED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.CONFIRMED);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.readyOrder(999L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    void 주문_준비완료_처리_시_상태가_CONFIRMED가_아니면_ORDER_INVALID_STATUS() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PENDING);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.readyOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
    }

    // ──────────────────── completeOrder ────────────────────

    @Test
    void 주문_완료_처리_성공_온라인결제() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.READY);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.completeOrder(OWNER_ID, ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 주문_완료_처리_성공_현장결제() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.READY);
        Payment payment = createPayment(order, customer, PaymentMethod.CASH_ON_SITE, PaymentStatus.NOT_PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.completeOrder(OWNER_ID, ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 주문_완료_처리_시_주문이_없으면_ORDER_NOT_FOUND() {
        // given
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.completeOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 주문_완료_처리_시_상점주가_아니면_STORE_ACCESS_DENIED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.READY);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.completeOrder(999L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    void 주문_완료_처리_시_상태가_READY가_아니면_ORDER_INVALID_STATUS() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.CONFIRMED);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.completeOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);

        verify(paymentRepository, never()).findByOrder_OrderId(any());
    }

    @Test
    void 주문_완료_처리_시_결제정보가_없으면_PAYMENT_NOT_FOUND() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.READY);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.completeOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 주문_완료_처리_시_현장결제가_이미_결제됐으면_ORDER_INVALID_STATUS() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.READY);
        Payment payment = createPayment(order, customer, PaymentMethod.CASH_ON_SITE, PaymentStatus.PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> storeOrderService.completeOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
    }

    @Test
    void 주문_완료_처리_시_온라인결제가_PAID가_아니면_PAYMENT_NOT_COMPLETED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.READY);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PENDING);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> storeOrderService.completeOrder(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_COMPLETED);
    }

    // ──────────────────── rejectOrder ────────────────────

    private void stubLockToRunImmediately() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(ErrorCode.class), any(Runnable.class));
    }

    @Test
    void 주문_거절_성공_온라인결제완료건은_PortOne_취소후_CANCELLED() {
        // given
        stubLockToRunImmediately();

        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PAID);

        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "재고 부족");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(orderService).restoreStockForOrder(ORDER_ID);
        verify(portOnePaymentClient).cancelPayment(payment.getPortonePaymentId(), payment.getAmount(), "재고 부족");
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 주문_거절_성공_결제대기건은_PortOne_호출없이_CANCELLED() {
        // given
        stubLockToRunImmediately();

        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PENDING);

        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "사장 사정");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(orderService).restoreStockForOrder(ORDER_ID);
        verify(portOnePaymentClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    void 주문_거절_성공_현장결제_미결제건은_결제상태_유지() {
        // given
        stubLockToRunImmediately();

        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PENDING);
        Payment payment = createPayment(order, customer, PaymentMethod.CASH_ON_SITE, PaymentStatus.NOT_PAID);

        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "재고 부족");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NOT_PAID);
        verify(portOnePaymentClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    void 주문_거절_시_락_획득_실패하면_LOCK_ORDER_FAILED() {
        // given
        doThrow(new BusinessException(ErrorCode.LOCK_ORDER_FAILED))
                .when(redisLockService)
                .executeWithLock(anyString(), any(Duration.class), any(ErrorCode.class), any(Runnable.class));

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ORDER_FAILED);

        verify(orderRepository, never()).findByIdWithPessimisticLock(any());
    }

    @Test
    void 주문_거절_시_주문이_없으면_ORDER_NOT_FOUND() {
        // given
        stubLockToRunImmediately();
        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 주문_거절_시_상점주가_아니면_STORE_ACCESS_DENIED() {
        // given
        stubLockToRunImmediately();

        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);

        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectOrder(999L, ORDER_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    void 주문_거절_시_상태가_PENDING_PAID가_아니면_ORDER_INVALID_STATUS() {
        // given
        stubLockToRunImmediately();

        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.CONFIRMED);

        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);

        verify(orderService, never()).restoreStockForOrder(any());
    }

    @Test
    void 주문_거절_시_결제정보가_없으면_PAYMENT_NOT_FOUND() {
        // given
        stubLockToRunImmediately();

        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);

        when(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectOrder(OWNER_ID, ORDER_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        verify(orderService, never()).restoreStockForOrder(any());
    }

    // ──────────────────── approveRefund ────────────────────

    @Test
    void 환불_승인_성공() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PAID);
        payment.requestRefund("단순 변심");

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.approveRefund(OWNER_ID, ORDER_ID);

        // then
        assertThat(payment.getRefundStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderService).restoreStockForOrder(ORDER_ID);
        verify(portOnePaymentClient).cancelPayment(payment.getPortonePaymentId(), payment.getAmount(), "단순 변심");
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 환불_승인_시_주문이_없으면_ORDER_NOT_FOUND() {
        // given
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.approveRefund(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 환불_승인_시_상점주가_아니면_STORE_ACCESS_DENIED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.approveRefund(999L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    void 환불_승인_시_결제정보가_없으면_PAYMENT_NOT_FOUND() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.approveRefund(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 환불_승인_시_환불요청상태가_아니면_PAYMENT_REFUND_NOT_REQUESTED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PAID);
        // 환불 요청 안 한 상태 (refundStatus = null)

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> storeOrderService.approveRefund(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_REFUND_NOT_REQUESTED);

        verify(portOnePaymentClient, never()).cancelPayment(any(), any(), any());
        verify(orderService, never()).restoreStockForOrder(any());
    }

    // ──────────────────── rejectRefund ────────────────────

    @Test
    void 환불_거절_성공() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PAID);
        payment.requestRefund("단순 변심");

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        // when
        storeOrderService.rejectRefund(OWNER_ID, ORDER_ID);

        // then
        assertThat(payment.getRefundStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void 환불_거절_시_주문이_없으면_ORDER_NOT_FOUND() {
        // given
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectRefund(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 환불_거절_시_상점주가_아니면_STORE_ACCESS_DENIED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectRefund(999L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    void 환불_거절_시_결제정보가_없으면_PAYMENT_NOT_FOUND() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectRefund(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 환불_거절_시_환불요청상태가_아니면_PAYMENT_REFUND_NOT_REQUESTED() {
        // given
        Account owner = mock(Account.class);
        when(owner.getAccountId()).thenReturn(OWNER_ID);
        Account customer = mock(Account.class);

        Store store = createStore(owner);
        Order order = createOrder(store, customer, OrderStatus.PAID);
        Payment payment = createPayment(order, customer, PaymentMethod.CARD, PaymentStatus.PAID);
        // 환불 요청 안 한 상태 (refundStatus = null)

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> storeOrderService.rejectRefund(OWNER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_REFUND_NOT_REQUESTED);
    }
}
