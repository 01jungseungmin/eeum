package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 외부 취소 Webhook이 주문 락을 중첩 획득하지 않는지 고정한다.
 *
 * <p><b>왜 별도 테스트인가.</b> 기존 {@code PaymentServiceTest}는 {@link RedisLockService}를
 * Mockito mock으로 두고 supplier를 그대로 실행하도록 스텁한다. 그러면 "같은 키를 두 번 잡으면
 * 실패한다"는 락의 핵심 의미가 사라져, 중첩 획득 결함이 테스트를 통과해 버린다.
 * 여기서는 {@code SET NX} 의미를 그대로 흉내 내는 대역을 써서 구조적으로 검증한다.
 *
 * <p>실제로 이 경로에는 결함이 있었다. {@code handleWebhook}이 주문 락을 쥔 채
 * {@code PaymentCancellationService.cancel}을 호출했고, cancel이 같은 키를 다시 잡으면서
 * 외부 취소 Webhook이 항상 {@code LOCK_ORDER_FAILED}로 실패했다. PortOne이 재전송해도
 * 같은 지점에서 다시 실패해 내부 상태가 영구히 동기화되지 않았다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWebhookCancellationLockTest {

    private static final Long ORDER_ID = 1L;
    private static final String PAYMENT_ID = "portone-payment-1";
    private static final String RAW_BODY =
            "{\"type\":\"Transaction.Cancelled\",\"data\":{\"paymentId\":\"" + PAYMENT_ID + "\"}}";

    // Base64로 디코딩 가능한 시크릿이어야 서명 검증이 실제로 수행된다.
    private static final String WEBHOOK_SECRET = Base64.getEncoder()
            .encodeToString("eeum-webhook-secret".getBytes(StandardCharsets.UTF_8));

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private PortOnePaymentClient portOnePaymentClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.eeum.eeum.application.ai.service.AiPlanSubscriptionService aiPlanSubscriptionService;
    @Mock private OperationFailureRecorder operationFailureRecorder;
    @Mock private RateLimitService rateLimitService;
    @Mock private OwnerRevenueService ownerRevenueService;
    @Mock private PaymentCancellationService paymentCancellationService;
    @Mock private PaymentWebhookProcessor paymentWebhookProcessor;

    private NonReentrantLockStub lockStub;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        lockStub = new NonReentrantLockStub();
        PortOneProperties portOneProperties =
                new PortOneProperties("test-secret", "https://api.portone.io", WEBHOOK_SECRET);
        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                lockStub,
                portOneProperties,
                orderService,
                portOnePaymentClient,
                new ObjectMapper(),
                eventPublisher,
                operationFailureRecorder,
                rateLimitService,
                ownerRevenueService,
                paymentCancellationService,
                paymentWebhookProcessor,
                new PaymentVerificationProcessor(orderRepository, paymentRepository, ownerRevenueService, eventPublisher),
                aiPlanSubscriptionService
        );

        when(paymentRepository.findOrderIdByPortonePaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(ORDER_ID));
    }

    @Test
    void 외부_취소_Webhook은_주문_락을_중첩_획득하지_않는다() {
        // given — PortOne 조회 결과가 이미 취소된 결제다
        givenExternalStatus("CANCELLED");

        // 실제 cancel()은 스스로 같은 주문 락을 잡는다. 그 동작을 그대로 흉내 낸다 —
        // 호출부가 바깥에서 락을 쥐고 있으면 여기서 LOCK_ORDER_FAILED가 난다.
        doAnswer(invocation -> lockStub.executeWithLock(
                LockKeys.order(ORDER_ID), Duration.ofSeconds(30), ErrorCode.LOCK_ORDER_FAILED,
                (Supplier<Void>) () -> null))
                .when(paymentCancellationService)
                .cancel(eq(ORDER_ID), eq(PaymentCancellationTrigger.PORTONE_WEBHOOK), anyString(), eq(true));

        // when & then
        assertThatCode(() -> paymentService.handleWebhook(RAW_BODY, signedHeaders()))
                .doesNotThrowAnyException();

        verify(paymentCancellationService).cancel(
                eq(ORDER_ID), eq(PaymentCancellationTrigger.PORTONE_WEBHOOK), anyString(), eq(true));
        // 락이 제대로 반납됐는지까지 본다 — 남아 있으면 다음 Webhook이 막힌다.
        assertThat(lockStub.heldKeys()).isEmpty();
    }

    @Test
    void 결제완료_Webhook은_주문_락_안에서_반영된다() {
        // given — 취소가 아닌 정상 결제 완료 Webhook
        givenExternalStatus("PAID");

        // when
        paymentService.handleWebhook(RAW_BODY, signedHeaders());

        // then — 반영은 주문 락 안에서 이뤄지고, 끝난 뒤 반납된다
        verify(paymentWebhookProcessor).applyPaidWebhook(eq(ORDER_ID), eq(PAYMENT_ID), any());
        assertThat(lockStub.acquiredKeys()).contains(LockKeys.order(ORDER_ID));
        assertThat(lockStub.heldKeys()).isEmpty();
    }

    @Test
    void 부분_취소_Webhook은_자동_반영하지_않고_이력만_남긴다() {
        // given — 부분 취소는 누적 취소 금액 차액 계산이 필요해 이번 범위가 아니다
        givenExternalStatus("PARTIAL_CANCELLED");

        // when
        paymentService.handleWebhook(RAW_BODY, signedHeaders());

        // then
        verify(operationFailureRecorder).record(
                any(), eq("PaymentService.handleWebhook.partialCancel"),
                eq("order"), eq(String.valueOf(ORDER_ID)),
                eq("PARTIAL_CANCEL_NOT_SUPPORTED"), anyString(), anyString());
        verify(paymentWebhookProcessor, org.mockito.Mockito.never())
                .applyPaidWebhook(any(), any(), any());
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void givenExternalStatus(String status) {
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(
                PortOnePaymentInfo.builder()
                        .paymentId(PAYMENT_ID)
                        .status(status)
                        .amount(java.math.BigDecimal.valueOf(10000))
                        .pgProvider("TEST")
                        .build());
    }

    private HttpHeaders signedHeaders() {
        String webhookId = "webhook-1";
        String timestamp = "1700000000";
        HttpHeaders headers = new HttpHeaders();
        headers.add("webhook-id", webhookId);
        headers.add("webhook-timestamp", timestamp);
        headers.add("webhook-signature", "v1," + sign(webhookId, timestamp));
        return headers;
    }

    private String sign(String webhookId, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(WEBHOOK_SECRET), "HmacSHA256"));
            byte[] signature = mac.doFinal(
                    (webhookId + "." + timestamp + "." + RAW_BODY).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@code SET NX} 의미를 그대로 지키는 락 대역.
     *
     * <p>이미 잡혀 있는 키를 다시 잡으면 실패한다 — 운영에서 쓰는 Redis 락과 같다.
     * supplier를 그대로 실행하는 mock으로는 이 성질이 사라져 중첩 획득을 잡지 못한다.
     */
    private static final class NonReentrantLockStub extends RedisLockService {

        private final Set<String> held = new HashSet<>();
        private final Set<String> acquired = new HashSet<>();

        private NonReentrantLockStub() {
            super(null);
        }

        @Override
        public <T> T executeWithLock(String key, Duration leaseTime, ErrorCode errorCode, Supplier<T> supplier) {
            if (!held.add(key)) {
                throw new BusinessException(errorCode);
            }
            acquired.add(key);
            try {
                return supplier.get();
            } finally {
                held.remove(key);
            }
        }

        @Override
        public void executeWithLock(String key, Duration leaseTime, ErrorCode errorCode, Runnable runnable) {
            executeWithLock(key, leaseTime, errorCode, () -> {
                runnable.run();
                return null;
            });
        }

        Set<String> heldKeys() {
            return held;
        }

        Set<String> acquiredKeys() {
            return acquired;
        }
    }
}
