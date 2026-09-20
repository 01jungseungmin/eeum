package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 늦은 결제·외부 취소 Webhook이 실제 MySQL 행 잠금과 Redis 주문 락 위에서 한 번만, 끝까지 수렴하는지 고정한다.
 *
 * PortOne만 대역으로 바꾸고 서명 검증부터 원장 반영까지 운영 경로를 그대로 탄다.
 * Webhook 시크릿은 Standard Webhooks 규격(Base64)이어야 서명 검증이 실제로 돈다.
 */
@EnabledIfDockerAvailable
@TestPropertySource(properties = "portone.webhook-secret=" + LatePaymentWebhookIntegrationTest.WEBHOOK_SECRET)
@RequiredArgsConstructor
class LatePaymentWebhookIntegrationTest extends IntegrationTestSupport {

    static final String WEBHOOK_SECRET = "ZWV1bS1pbnRlZ3JhdGlvbi13ZWJob29rLXNlY3JldA==";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    @MockitoBean
    private PortOnePaymentClient portOnePaymentClient;

    private final PaymentService paymentService;
    private final PaymentCancellationService paymentCancellationService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancellationOperationRepository cancellationOperationRepository;
    private final OperationFailureLogRepository operationFailureLogRepository;

    private Account buyer;
    private Store store;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(Account.createOwner(
                "late-webhook-owner@test.com", "encoded_pw", "늦은결제 사장", "010-7100-0001"));
        buyer = accountRepository.save(Account.createUser(
                "late-webhook-buyer@test.com", "encoded_pw", "구매자", "늦은결제구매자", "010-7100-0002"));
        store = storeRepository.save(Store.createForOwnerSignup(
                owner, "늦은결제 상점", "서울시 마포구", "02-7100-0001"));
    }

    @AfterEach
    void cleanup() {
        cancellationOperationRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        operationFailureLogRepository.deleteAllInBatch();
    }

    // ─────────────────── 늦은 결제 (만료 주문에 도착한 PAID) ───────────────────

    @Test
    void 만료된_주문에_늦게_결제되면_PG를_한_번_취소하고_주문은_만료로_남긴다() {
        // given
        Payment payment = saveExpiredOrderPayment();
        givenPortOneStatus(payment, "PAID", null);
        givenPgCancelSucceeds();

        // when
        sendWebhook(payment);

        // then
        verify(portOnePaymentClient, times(1)).cancelPayment(
                eq(payment.getPortonePaymentId()), any(), anyString(), anyString());
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.COMPLETED);
    }

    @Test
    void 늦은_결제_취소가_PG_실패로_끊겨도_재전송된_Webhook이_취소를_끝낸다() {
        // given — 첫 PG 취소 호출은 실패한다
        Payment payment = saveExpiredOrderPayment();
        givenPortOneStatus(payment, "PAID", null);
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("PG timeout"))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED, "cancel-1", AMOUNT));

        // when — 첫 Webhook은 실패해 PortOne 재전송을 기다린다
        assertThatThrownBy(() -> sendWebhook(payment))
                .isInstanceOf(RuntimeException.class);

        // then — 결제는 되살아났지만 돈은 아직 고객에게 돌아가지 않은 상태
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PAID);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.PENDING);

        // when — PortOne 재전송
        sendWebhook(payment);

        // then — 같은 경로로 PG 취소를 다시 시도해 끝까지 수렴한다
        verify(portOnePaymentClient, times(2)).cancelPayment(
                eq(payment.getPortonePaymentId()), any(), anyString(), anyString());
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.COMPLETED);
    }

    @Test
    void 환불이_끝난_뒤_도착한_PAID_재전송은_결제를_되살리지_않고_PG도_다시_부르지_않는다() {
        // given — 늦은 결제 취소가 이미 끝났다
        Payment payment = saveExpiredOrderPayment();
        givenPortOneStatus(payment, "PAID", null);
        givenPgCancelSucceeds();
        sendWebhook(payment);

        // when — 조회 시점 차이로 PAID를 본 재전송이 늦게 도착한다
        sendWebhook(payment);

        // then
        verify(portOnePaymentClient, times(1)).cancelPayment(
                eq(payment.getPortonePaymentId()), any(), anyString(), anyString());
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.COMPLETED);
    }

    @Test
    void 같은_늦은_결제_Webhook이_동시에_와도_PG_취소는_한_번만_호출된다() throws Exception {
        // given — PG 취소가 느려 두 요청이 겹친다
        Payment payment = saveExpiredOrderPayment();
        givenPortOneStatus(payment, "PAID", null);
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    sleepQuietly(500);
                    return new PortOneCancelResult(PortOneCancelResult.SUCCEEDED, "cancel-1", AMOUNT);
                });

        // when
        List<Throwable> failures = runConcurrently(2,
                () -> sendWebhook(payment));

        // then — 겹친 요청은 주문 락에서 막혀 재전송을 기다릴 뿐, 돈을 두 번 돌려주지 않는다
        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ORDER_FAILED));
        verify(portOnePaymentClient, times(1)).cancelPayment(
                eq(payment.getPortonePaymentId()), any(), anyString(), anyString());
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.COMPLETED);
    }

    // ─────────────────── 외부 취소 Webhook ───────────────────

    @Test
    void PG_취소_호출이_실패해_PENDING인_작업도_콘솔_취소_Webhook으로_내부_원장까지_취소된다() {
        // given — 고객 취소 중 PG 호출이 실패해 작업이 PENDING으로 남았다
        Payment payment = savePaidOrderPayment();
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("PG timeout"));
        assertThatThrownBy(() -> paymentCancellationService.cancel(
                orderId(payment), PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소"))
                .isInstanceOf(RuntimeException.class);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.PENDING);

        // when — 운영자가 PortOne 콘솔에서 전액 취소했다
        givenPortOneStatus(payment, "CANCELLED", AMOUNT);
        sendWebhook(payment);

        // then — PG를 다시 부르지 않고 내부 결제·주문·작업을 확정 취소로 맞춘다
        verify(portOnePaymentClient, times(1)).cancelPayment(anyString(), any(), anyString(), anyString());
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.COMPLETED);
    }

    @Test
    void REQUESTED로_격리된_취소는_전액_취소_Webhook이_오면_확정된다() {
        // given — PG가 비동기 취소(REQUESTED)만 돌려줘 수동 검토로 격리됐다
        Payment payment = savePaidOrderPayment();
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.REQUESTED, "cancel-1", null));
        assertThatThrownBy(() -> paymentCancellationService.cancel(
                orderId(payment), PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소"))
                .isInstanceOf(BusinessException.class);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PAID);

        // when — 최종 CANCELLED Webhook
        givenPortOneStatus(payment, "CANCELLED", AMOUNT);
        sendWebhook(payment);

        // then
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(operationStatus(payment)).isEqualTo(PaymentCancellationStatus.COMPLETED);
    }

    @Test
    void PortOne_누적_취소액이_결제액과_다르면_외부_취소를_확정하지_않는다() {
        // given
        Payment payment = savePaidOrderPayment();
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.REQUESTED, "cancel-1", null));
        assertThatThrownBy(() -> paymentCancellationService.cancel(
                orderId(payment), PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소"))
                .isInstanceOf(BusinessException.class);

        // when & then
        givenPortOneStatus(payment, "CANCELLED", new BigDecimal("4000"));
        assertThatThrownBy(() -> sendWebhook(payment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(payment)).isEqualTo(OrderStatus.PAID);
    }

    // ─────────────────── 픽스처 ───────────────────

    private Payment saveExpiredOrderPayment() {
        Order order = Order.create(buyer, store, AMOUNT, "LATE-" + UUID.randomUUID(), OrderType.SALE, null, null);
        order.expire();
        orderRepository.save(order);
        Payment payment = Payment.create(order, buyer, "late-pay-" + UUID.randomUUID(), UUID.randomUUID().toString(),
                AMOUNT, PaymentMethod.CARD, PaymentStatus.PENDING);
        payment.cancel();
        return paymentRepository.save(payment);
    }

    private Payment savePaidOrderPayment() {
        Order order = Order.create(buyer, store, AMOUNT, "PAID-" + UUID.randomUUID(), OrderType.SALE, null, null);
        order.markAsPaid();
        orderRepository.save(order);
        Payment payment = Payment.create(order, buyer, "paid-pay-" + UUID.randomUUID(), UUID.randomUUID().toString(),
                AMOUNT, PaymentMethod.CARD, PaymentStatus.PENDING);
        payment.markAsPaid("TEST");
        return paymentRepository.save(payment);
    }

    private void givenPortOneStatus(Payment payment, String status, BigDecimal cancelledAmount) {
        when(portOnePaymentClient.getPayment(payment.getPortonePaymentId())).thenReturn(
                PortOnePaymentInfo.builder()
                        .paymentId(payment.getPortonePaymentId())
                        .status(status)
                        .amount(AMOUNT)
                        .cancelledAmount(cancelledAmount)
                        .pgProvider("TEST")
                        .build());
    }

    private void givenPgCancelSucceeds() {
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.SUCCEEDED, "cancel-1", AMOUNT));
    }

    private Long orderId(Payment payment) {
        return payment.getOrder().getOrderId();
    }

    private PaymentStatus paymentStatus(Payment payment) {
        return paymentRepository.findById(payment.getPaymentId()).orElseThrow().getStatus();
    }

    private OrderStatus orderStatus(Payment payment) {
        return orderRepository.findById(orderId(payment)).orElseThrow().getStatus();
    }

    private PaymentCancellationStatus operationStatus(Payment payment) {
        return cancellationOperationRepository.findByOrder_OrderId(orderId(payment))
                .map(PaymentCancellationOperation::getStatus)
                .orElse(null);
    }

    // 서명은 본문 문자열 그대로에 대해 계산한다 — 본문을 한 번만 만들어 두 곳에 같이 쓴다.
    private void sendWebhook(Payment payment) {
        String rawBody = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\""
                + payment.getPortonePaymentId() + "\"}}";
        paymentService.handleWebhook(rawBody, signedHeaders(rawBody));
    }

    private HttpHeaders signedHeaders(String rawBody) {
        String webhookId = "wh-" + UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("webhook-id", webhookId);
        headers.add("webhook-timestamp", timestamp);
        headers.add("webhook-signature", "v1," + sign(webhookId, timestamp, rawBody));
        return headers;
    }

    private String sign(String webhookId, String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(WEBHOOK_SECRET), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal((webhookId + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Throwable> runConcurrently(int threads, Runnable task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    awaitQuietly(start);
                    try {
                        task.run();
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return failures;
    }
}
