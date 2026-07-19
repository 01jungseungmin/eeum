package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    // PortOneProperties는 record(final class)이므로 @InjectMocks 대신 수동 생성
    private PaymentService paymentService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private OrderService orderService;
    @Mock private PortOnePaymentClient portOnePaymentClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.eeum.eeum.application.ai.service.AiPlanSubscriptionService aiPlanSubscriptionService;

    @BeforeEach
    void setUp() {
        PortOneProperties portOneProperties = new PortOneProperties("test-secret", "https://api.portone.io", "test-webhook-secret");
        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                redisLockService,
                portOneProperties,
                orderService,
                portOnePaymentClient,
                new ObjectMapper(),
                eventPublisher,
                aiPlanSubscriptionService
        );
    }

    // ──────────────────── verifyPayment ────────────────────

    // [시나리오 3] 이벤트 종료 전 생성된 PENDING 주문은 이벤트 종료 여부와 무관하게 결제 완료
    @Test
    void 이벤트_종료_후_PENDING_주문의_결제_완료_성공() {
        // given
        Long accountId = 100L;
        String orderNumber = "ORD-20260616-TEST001";
        String portonePaymentId = "pay_20260616_test001";
        BigDecimal totalPrice = BigDecimal.valueOf(8000);

        Account customerAccount = mock(Account.class);
        when(customerAccount.getAccountId()).thenReturn(accountId);
        when(customerAccount.getName()).thenReturn("고객");

        Account ownerAccount = mock(Account.class);
        when(ownerAccount.getAccountId()).thenReturn(200L);

        Store store = mock(Store.class);
        when(store.getName()).thenReturn("테스트 상점");
        when(store.getAccount()).thenReturn(ownerAccount);

        // PENDING 상태의 주문 (이벤트가 끝났어도 주문 자체는 유효)
        Order order = Order.create(customerAccount, store, totalPrice, orderNumber, OrderType.SALE, null, null);
        ReflectionTestUtils.setField(order, "orderId", 10L);

        // PENDING 상태의 결제
        Payment payment = Payment.create(order, customerAccount, portonePaymentId, orderNumber,
                totalPrice, PaymentMethod.EASY_PAY, PaymentStatus.PENDING);
        ReflectionTestUtils.setField(payment, "paymentId", 20L);

        // PortOne에서 PAID 응답
        PortOnePaymentInfo paymentInfo = PortOnePaymentInfo.builder()
                .paymentId(portonePaymentId)
                .status("PAID")
                .amount(totalPrice)
                .pgProvider("kakaopay")
                .build();

        PaymentCompleteRequestDto request = new PaymentCompleteRequestDto();
        ReflectionTestUtils.setField(request, "paymentId", portonePaymentId);
        ReflectionTestUtils.setField(request, "orderNumber", orderNumber);

        // 분산 락 내 로직을 실제로 실행
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return null;
        }).when(redisLockService).executeWithLock(
                anyString(), any(Duration.class), any(ErrorCode.class), any(Runnable.class));

        when(orderRepository.findByOrderNumberWithPessimisticLock(orderNumber))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(10L))
                .thenReturn(Optional.of(payment));
        when(portOnePaymentClient.getPayment(portonePaymentId)).thenReturn(paymentInfo);

        // when
        paymentService.verifyPayment(accountId, request);

        // then — 이벤트 상품 상태와 무관하게 결제 및 주문 상태가 PAID로 전환
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
        verify(eventPublisher).publishEvent(any(OrderPaidEvent.class));
    }

    @Test
    void 이미_결제된_주문에_결제_완료_재요청_시_PAYMENT_DUPLICATE() {
        // given
        Long accountId = 100L;
        String orderNumber = "ORD-20260616-TEST002";
        String portonePaymentId = "pay_20260616_test002";
        BigDecimal totalPrice = BigDecimal.valueOf(8000);

        Account customerAccount = mock(Account.class);
        when(customerAccount.getAccountId()).thenReturn(accountId);

        Store store = mock(Store.class);

        Order order = Order.create(customerAccount, store, totalPrice, orderNumber, OrderType.SALE, null, null);
        ReflectionTestUtils.setField(order, "orderId", 10L);
        order.markAsPaid();

        Payment payment = Payment.create(order, customerAccount, portonePaymentId, orderNumber,
                totalPrice, PaymentMethod.EASY_PAY, PaymentStatus.PAID);
        ReflectionTestUtils.setField(payment, "paymentId", 20L);

        PaymentCompleteRequestDto request = new PaymentCompleteRequestDto();
        ReflectionTestUtils.setField(request, "paymentId", portonePaymentId);
        ReflectionTestUtils.setField(request, "orderNumber", orderNumber);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return null;
        }).when(redisLockService).executeWithLock(
                anyString(), any(Duration.class), any(ErrorCode.class), any(Runnable.class));

        when(orderRepository.findByOrderNumberWithPessimisticLock(orderNumber))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(10L))
                .thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentService.verifyPayment(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_DUPLICATE);
    }

    @Test
    void 만료된_주문에_결제_완료_요청_시_ORDER_EXPIRED() {
        // given
        Long accountId = 100L;
        String orderNumber = "ORD-20260616-TEST003";
        String portonePaymentId = "pay_20260616_test003";
        BigDecimal totalPrice = BigDecimal.valueOf(8000);

        Account customerAccount = mock(Account.class);
        when(customerAccount.getAccountId()).thenReturn(accountId);

        Store store = mock(Store.class);

        Order order = Order.create(customerAccount, store, totalPrice, orderNumber, OrderType.SALE, null, null);
        ReflectionTestUtils.setField(order, "orderId", 10L);
        order.expire();

        Payment payment = Payment.create(order, customerAccount, portonePaymentId, orderNumber,
                totalPrice, PaymentMethod.EASY_PAY, PaymentStatus.PENDING);
        ReflectionTestUtils.setField(payment, "paymentId", 20L);

        PaymentCompleteRequestDto request = new PaymentCompleteRequestDto();
        ReflectionTestUtils.setField(request, "paymentId", portonePaymentId);
        ReflectionTestUtils.setField(request, "orderNumber", orderNumber);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return null;
        }).when(redisLockService).executeWithLock(
                anyString(), any(Duration.class), any(ErrorCode.class), any(Runnable.class));

        when(orderRepository.findByOrderNumberWithPessimisticLock(orderNumber))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdWithPessimisticLock(10L))
                .thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentService.verifyPayment(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_EXPIRED);
    }
}
