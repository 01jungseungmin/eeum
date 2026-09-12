package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.dto.request.PaymentWebhookRequestDto;
import com.eeum.eeum.application.order.dto.request.RefundRequestDto;
import com.eeum.eeum.application.order.dto.response.PaymentResponseDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.lock.RateLimitKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RedisLockService redisLockService;
    private final PortOneProperties portOneProperties;
    private final OrderService orderService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final OperationFailureRecorder operationFailureRecorder;
    private final RateLimitService rateLimitService;
    private final OwnerRevenueService ownerRevenueService;
    private final PaymentCancellationService paymentCancellationService;
    private final PaymentWebhookProcessor paymentWebhookProcessor;
    private final PaymentVerificationProcessor paymentVerificationProcessor;
    private final com.eeum.eeum.application.ai.service.AiPlanSubscriptionService aiPlanSubscriptionService;

    // PortOne Webhook/REST의 연결·읽기 timeout(각 30초)보다 충분히 길게 둔다.
    // DB 락은 이 구간에 잡지 않으며, 같은 주문의 상태 변경 진입만 직렬화한다.
    private static final Duration PAYMENT_LOCK_LEASE_TIME = Duration.ofMinutes(2);

    /** Webhook 서명 실패 누적 카운터의 집계 구간. */
    private static final Duration SIGNATURE_FAILURE_COUNT_WINDOW = Duration.ofHours(1);

    /** 서명 실패를 DB 이력으로 남기는 최소 간격 — 구간당 1건. */
    private static final Duration SIGNATURE_FAILURE_RECORD_COOLDOWN = Duration.ofMinutes(10);

    // PortOne은 실패한 Webhook을 최대 256분까지 재전송한다. 재전송에서도 최초 event
    // timestamp가 유지되므로, 그 범위를 넘는 작은 허용값을 쓰면 정상 이벤트를 버리게 된다.
    private static final Duration WEBHOOK_TIMESTAMP_TOLERANCE = Duration.ofHours(5);
    private static final Duration WEBHOOK_REPLAY_WINDOW = Duration.ofHours(6);

    public void verifyPayment(Long accountId, PaymentCompleteRequestDto request) {
        // 식별자 해소는 상태를 바꾸지 않는 짧은 조회다. 이후부터 외부 조회까지 같은 주문 락을
        // 유지해, PG가 이미 PAID인 동안 사장이 PENDING 주문을 거절하는 경합을 막는다.
        Long orderId = orderRepository.findByOrderNumber(request.getOrderNumber())
                .map(Order::getOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                PAYMENT_LOCK_LEASE_TIME,
                ErrorCode.LOCK_PAYMENT_FAILED,
                () -> verifyPaymentWithOrderLock(accountId, request, orderId)
        );
    }

    private void verifyPaymentWithOrderLock(Long accountId, PaymentCompleteRequestDto request, Long expectedOrderId) {
        // prepare/apply는 각각 짧은 트랜잭션이다. PortOne 호출은 두 트랜잭션 사이에만 존재한다.
        Long preparedOrderId = paymentVerificationProcessor.prepare(accountId, request);
        if (!expectedOrderId.equals(preparedOrderId)) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
        PortOnePaymentInfo paymentInfo = recordPortOneFailure(
                OperationFailureCategory.EXTERNAL_API, "PaymentService.verifyPayment.getPayment",
                request.getPaymentId(), "orderId=" + preparedOrderId,
                () -> portOnePaymentClient.getPayment(request.getPaymentId()));
        paymentVerificationProcessor.apply(accountId, request, paymentInfo);
    }

    public void handleWebhook(String rawBody, HttpHeaders headers) {
        // [1단계] 서명 검증 이전의 형식 오류는 이력에 남기지 않는다.
        // 이 엔드포인트는 permitAll이라 누구나 호출할 수 있고, 건별로 기록하면
        // 익명 요청 반복만으로 실패 이력 테이블을 채울 수 있다(3개월 보존).
        if (rawBody == null || rawBody.isBlank()) {
            log.warn("빈 Webhook body 수신");
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_MALFORMED);
        }

        /*
         * 1. PortOne Webhook Secret 또는 검증 키 환경변수 등록
         * 2. rawBody 기반 HMAC 서명 생성
         * 3. 요청 헤더 signature 값과 서버 생성 서명 비교
         * 4. 서명 불일치 시 Webhook 처리 중단
         * 5. 실패 로그 기록 및 401/400 계열 예외 처리
         */
        String webhookId = validateWebhookSignature(rawBody, headers);
        String replayKey = RateLimitKeys.webhookReplay(webhookId);
        if (!rateLimitService.tryAcquireCooldown(replayKey, WEBHOOK_REPLAY_WINDOW)) {
            log.info("중복 PortOne Webhook 무시: webhookId={}", webhookId);
            return;
        }
        try {
            PaymentWebhookRequestDto request = parseWebhookBody(rawBody);

            // [3단계] 여기부터는 서명 검증을 통과한 요청이다 — 실패는 빠짐없이 기록한다.
            String paymentId = request.resolvedPaymentId();
            if (paymentId == null || paymentId.isBlank()) {
                log.warn("paymentId 없는 Webhook 수신");
                operationFailureRecorder.record(
                        OperationFailureCategory.PAYMENT_WEBHOOK,
                        "PaymentService.handleWebhook",
                        null, null,
                        "WEBHOOK_NO_PAYMENT_ID", "Webhook에 paymentId가 없음", maskWebhookBody(rawBody));
                return;
            }

            Long orderId = paymentRepository.findOrderIdByPortonePaymentId(paymentId).orElse(null);
            if (orderId == null) {
                if (paymentId.startsWith(com.eeum.eeum.application.ai.service.AiPlanPaymentCommandExecutor.AI_PLAN_PAYMENT_PREFIX)) {
                    aiPlanSubscriptionService.handleWebhook(paymentId);
                    return;
                }
                log.warn("등록되지 않은 paymentId Webhook 수신: paymentId={}", paymentId);
                return;
            }
            handleWebhookByExternalStatus(orderId, paymentId);
        } catch (RuntimeException e) {
            // 처리 실패는 PortOne 재전송으로 복구해야 한다. 성공 여부와 무관하게 event id를
            // 완료 처리하면 일시적인 PG 조회·DB·락 실패가 영구 유실된다.
            rateLimitService.releaseCooldown(replayKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponseDto> getMyPayments(Long accountId, Pageable pageable) {
        return paymentRepository
                .findByOrder_Account_AccountId(accountId, pageable)
                .map(PaymentResponseDto::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentDetail(Long accountId, Long paymentId) {
        Payment payment = paymentRepository
                .findByOrder_Account_AccountIdAndPaymentId(accountId, paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentResponseDto.from(payment);
    }

    /**
     * 고객 결제 취소.
     *
     * <p>취소 절차 자체는 {@link PaymentCancellationService}가 맡는다. 이 메서드는
     * "누가 어떤 결제를 취소할 수 있는가"만 판단한다 — 네 진입점이 각자 취소를 구현하면
     * 경로마다 금전 처리가 갈린다.
     *
     * <p>{@code @Transactional}을 걸지 않는다. 취소 절차 안에서 PortOne을 호출하므로
     * 여기에 트랜잭션을 걸면 외부 호출이 다시 트랜잭션 안으로 들어온다.
     */
    public void cancelPayment(Long accountId, Long paymentId) {
        /*
         * 한 번만 읽는다. 이전에는 orderId와 reason을 각각 조회했는데, 두 조회 사이에
         * refundReason이 바뀌면 서로 다른 시점의 값이 섞인다. 게다가 두 메서드는 같은 빈
         * 안에서 호출돼 @Transactional이 프록시를 타지 못해 애초에 무효였다.
         *
         * 취소 대상의 최종 판정은 PaymentCancellationService가 주문 락과 비관적 락 아래에서
         * 다시 한다. 여기서 보는 값은 "이 사용자가 이 결제를 취소 요청할 수 있는가"까지다.
         */
        Payment payment = paymentRepository
                .findByOrder_Account_AccountIdAndPaymentId(accountId, paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException(ErrorCode.PAYMENT_INVALID_STATUS);
        }

        String reason = StringUtils.hasText(payment.getRefundReason())
                ? payment.getRefundReason()
                : "고객 요청 취소";

        paymentCancellationService.cancel(
                payment.getOrder().getOrderId(), PaymentCancellationTrigger.CUSTOMER_CANCEL, reason);
    }

    @Transactional
    public void requestRefund(Long accountId, Long paymentId, RefundRequestDto request) {
        Payment payment = paymentRepository
                .findByOrder_Account_AccountIdAndPaymentId(accountId, paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException(ErrorCode.PAYMENT_INVALID_STATUS);
        }
        if (payment.getRefundStatus() == RefundStatus.REQUESTED) {
            throw new ConflictException(ErrorCode.PAYMENT_REFUND_ALREADY);
        }

        payment.requestRefund(request.getReason());
    }

    /**
     * 외부 결제 상태에 따라 Webhook을 분기한다.
     *
     * <p><b>여기서 주문 락을 잡지 않는다.</b> 취소 분기가 호출하는
     * {@link PaymentCancellationService#cancel}이 같은 {@code LockKeys.order} 키를 스스로 잡는데,
     * {@link com.eeum.eeum.common.service.RedisLockService}는 {@code SET NX} 기반이라 재진입을
     * 지원하지 않는다. 바깥에서 감싸면 안쪽 획득이 반드시 실패해 외부 취소 Webhook이
     * 영구히 반영되지 않는다. 그래서 락은 실제로 필요한 분기가 각자 잡는다.
     *
     * <p>덤으로 PortOne 조회가 어떤 락도 쥐지 않은 채 수행된다.
     */
    private void handleWebhookByExternalStatus(Long orderId, String paymentId) {

        PortOnePaymentInfo externalPayment = recordPortOneFailure(
                OperationFailureCategory.EXTERNAL_API, "PaymentService.handleWebhook.getPayment",
                paymentId, "orderId=" + orderId,
                () -> portOnePaymentClient.getPayment(paymentId));

        if ("CANCELLED".equalsIgnoreCase(externalPayment.getStatus())) {
            // PG가 이미 취소됐으므로 재호출하지 않는다. Payment·Order·재고·정산은 공통 취소
            // 작업이 짧은 독립 트랜잭션으로 함께 반영한다. 주문 락은 cancel()이 잡는다.
            paymentCancellationService.cancel(orderId, PaymentCancellationTrigger.PORTONE_WEBHOOK,
                    "PortOne 외부 취소 Webhook", true);
            return;
        }
        if ("PARTIAL_CANCELLED".equalsIgnoreCase(externalPayment.getStatus())) {
            paymentCancellationService.recordExternalPartialCancellation(orderId);
            operationFailureRecorder.record(
                    OperationFailureCategory.REFUND,
                    "PaymentService.handleWebhook.partialCancel",
                    "order", String.valueOf(orderId),
                    "PARTIAL_CANCEL_NOT_SUPPORTED",
                    "부분 취소를 수동 검토로 격리해 정산 지급을 차단했습니다.",
                    "paymentId=" + paymentId + ", externalStatus=" + externalPayment.getStatus());
            return;
        }

        // 결제 완료 반영만 주문 락이 필요하다 — verify 경로와 같은 키를 쓴다.
        redisLockService.executeWithLock(
                LockKeys.order(orderId), PAYMENT_LOCK_LEASE_TIME, ErrorCode.LOCK_PAYMENT_FAILED,
                () -> paymentWebhookProcessor.applyPaidWebhook(orderId, paymentId, externalPayment));
    }

    private String validateWebhookSignature(String rawBody, HttpHeaders headers) {
        String secret = portOneProperties.webhookSecret();
        if (!StringUtils.hasText(secret)) {
            // 서버 설정 오류다. 공격자가 반복 유발할 수 있으므로 2단계와 같은 제한 기록을 쓴다.
            log.error("PortOne Webhook Secret이 설정되지 않았습니다.");
            recordWebhookSignatureFailure("Webhook Secret 미설정", null);
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }

        String webhookId = headers.getFirst("webhook-id");
        String timestamp = headers.getFirst("webhook-timestamp");
        List<String> signatures = headers.get("webhook-signature");
        if (!StringUtils.hasText(webhookId) || !StringUtils.hasText(timestamp)
                || signatures == null || signatures.isEmpty()) {
            log.warn("서명 헤더 없는 Webhook 수신");
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_MALFORMED);
        }
        if (!isTimestampWithinTolerance(timestamp)) {
            recordWebhookSignatureFailure("Webhook timestamp 허용 범위 초과", rawBody);
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
        if (!matchesStandardWebhookSignature(secret, webhookId, timestamp, rawBody, signatures)) {
            // [2단계] 서명은 왔는데 맞지 않는다 — 시크릿 로테이션 사고일 수도, 공격일 수도 있다.
            // 카운터로 전량 집계하고 이력은 구간당 1건만 남긴다.
            recordWebhookSignatureFailure("서명 불일치", rawBody);
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
        return webhookId;
    }

    private boolean isTimestampWithinTolerance(String timestamp) {
        try {
            Instant occurredAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            return !occurredAt.isBefore(Instant.now().minus(WEBHOOK_TIMESTAMP_TOLERANCE))
                    && !occurredAt.isAfter(Instant.now().plus(WEBHOOK_TIMESTAMP_TOLERANCE));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean matchesStandardWebhookSignature(
            String secret, String webhookId, String timestamp, String rawBody, List<String> signatures
    ) {
        try {
            // Standard Webhooks: base64(HMAC-SHA256(webhook-id.timestamp.raw-body)).
            String encodedSecret = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(encodedSecret), "HmacSHA256"));
            byte[] expected = mac.doFinal((webhookId + "." + timestamp + "." + rawBody)
                    .getBytes(StandardCharsets.UTF_8));
            for (String header : signatures) {
                for (String part : header.split("\\s+")) {
                    if (!part.startsWith("v1,")) continue;
                    byte[] candidate = Base64.getDecoder().decode(part.substring(3));
                    if (MessageDigest.isEqual(expected, candidate)) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * PortOne 호출 실패를 업무 맥락과 함께 <b>한 번만</b> 기록하고 예외를 그대로 다시 던진다.
     *
     * <p>클라이언트({@code PortOnePaymentClientImpl})는 이력을 남기지 않는다. 양쪽에서 남기면
     * 실패 1건이 이력 2건이 되어 failureCount·분류별 실패율·알람 임계치가 전부 두 배로 어긋난다.
     * 기록을 서비스에 두면 orderId·orderNumber 같은 업무 정보까지 payload에 담을 수 있다.
     */
    private <T> T recordPortOneFailure(
            OperationFailureCategory category,
            String operation,
            String refId,
            String payload,
            Supplier<T> call
    ) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            operationFailureRecorder.record(category, operation, "PAYMENT", refId, e, payload);
            throw e;
        }
    }

    private void recordPortOneFailure(
            OperationFailureCategory category,
            String operation,
            String refId,
            String payload,
            Runnable call
    ) {
        recordPortOneFailure(category, operation, refId, payload, () -> {
            call.run();
            return null;
        });
    }


    /**
     * [2단계] 서명 검증 실패의 제한 기록.
     *
     * <p>발생량은 Redis 카운터로 전량 집계하고, DB 이력은 {@link #SIGNATURE_FAILURE_RECORD_COOLDOWN}당
     * 1건만 남긴다. 인증 없는 엔드포인트라 건별로 남기면 익명 요청만으로 테이블이 불어난다.
     * 대신 남기는 1건에 구간 누적 건수를 적어 규모를 알 수 있게 한다.
     */
    private void recordWebhookSignatureFailure(String message, String rawBody) {
        long total = rateLimitService.incrementAndGet(
                RateLimitKeys.webhookSignatureFailureCount(), SIGNATURE_FAILURE_COUNT_WINDOW);

        if (!rateLimitService.tryAcquireCooldown(
                RateLimitKeys.webhookSignatureFailureRecord(), SIGNATURE_FAILURE_RECORD_COOLDOWN)) {
            log.warn("Webhook 서명 검증 실패(이력 생략) — reason={}, 누적={}건", message, total);
            return;
        }

        operationFailureRecorder.record(
                OperationFailureCategory.PAYMENT_WEBHOOK,
                "PaymentService.validateWebhookSignature",
                null, null,
                ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                message + " (최근 " + SIGNATURE_FAILURE_COUNT_WINDOW.toHours() + "시간 누적 " + total + "건)",
                maskWebhookBody(rawBody));
    }

    // 실패 이력은 관리자 화면에 그대로 노출되므로 원문을 통째로 넣지 않는다.
    // 재현에 필요한 앞부분만 남기고 자른다.
    private String maskWebhookBody(String rawBody) {
        if (rawBody == null) {
            return null;
        }
        int limit = Math.min(rawBody.length(), 500);
        return rawBody.substring(0, limit);
    }

    private PaymentWebhookRequestDto parseWebhookBody(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentWebhookRequestDto.class);
        } catch (JsonProcessingException e) {
            // [1단계] 형식 오류 — 기록하지 않고 400. 서명은 통과했더라도 본문이 JSON이 아니라면
            // 정상 PortOne 요청이 아니며, 여기서 남기면 본문 조작만으로 이력을 늘릴 수 있다.
            log.warn("Webhook body 파싱 실패: {}", maskWebhookBody(rawBody));
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_MALFORMED);
        }
    }
}
