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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;

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
    private final com.eeum.eeum.application.ai.service.AiPlanSubscriptionService aiPlanSubscriptionService;

    private static final Duration PAYMENT_LOCK_LEASE_TIME = Duration.ofSeconds(10);

    /** Webhook 서명 실패 누적 카운터의 집계 구간. */
    private static final Duration SIGNATURE_FAILURE_COUNT_WINDOW = Duration.ofHours(1);

    /** 서명 실패를 DB 이력으로 남기는 최소 간격 — 구간당 1건. */
    private static final Duration SIGNATURE_FAILURE_RECORD_COOLDOWN = Duration.ofMinutes(10);

    @Transactional
    public void verifyPayment(Long accountId, PaymentCompleteRequestDto request) {
        redisLockService.executeWithLock(
                LockKeys.orderNumber(request.getOrderNumber()),
                PAYMENT_LOCK_LEASE_TIME,
                ErrorCode.LOCK_PAYMENT_FAILED,
                () -> verifyPaymentWithLock(accountId, request)
        );
    }

    public void handleWebhook(String rawBody, String signature) {
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
        validateWebhookSignature(rawBody, signature);

        PaymentWebhookRequestDto request = parseWebhookBody(rawBody);

        // [3단계] 여기부터는 서명 검증을 통과한 요청이다 — 실패는 빠짐없이 기록한다.
        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            log.warn("paymentId 없는 Webhook 수신");
            operationFailureRecorder.record(
                    OperationFailureCategory.PAYMENT_WEBHOOK,
                    "PaymentService.handleWebhook",
                    null, null,
                    "WEBHOOK_NO_PAYMENT_ID", "Webhook에 paymentId가 없음", maskWebhookBody(rawBody));
            return;
        }

        redisLockService.executeWithLock(
                LockKeys.portonePayment(request.getPaymentId()),
                PAYMENT_LOCK_LEASE_TIME,
                ErrorCode.LOCK_PAYMENT_FAILED,
                () -> handleWebhookWithLock(request)
        );
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
        Long orderId = resolveOwnOrderId(accountId, paymentId);
        String reason = resolveCancelReason(accountId, paymentId);

        paymentCancellationService.cancel(
                orderId, PaymentCancellationTrigger.CUSTOMER_CANCEL, reason);
    }

    @Transactional(readOnly = true)
    protected Long resolveOwnOrderId(Long accountId, Long paymentId) {
        Payment payment = paymentRepository
                .findByOrder_Account_AccountIdAndPaymentId(accountId, paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException(ErrorCode.PAYMENT_INVALID_STATUS);
        }
        return payment.getOrder().getOrderId();
    }

    @Transactional(readOnly = true)
    protected String resolveCancelReason(Long accountId, Long paymentId) {
        return paymentRepository
                .findByOrder_Account_AccountIdAndPaymentId(accountId, paymentId)
                .map(Payment::getRefundReason)
                .filter(StringUtils::hasText)
                .orElse("고객 요청 취소");
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

    private void handleWebhookWithLock(PaymentWebhookRequestDto request) {
        /*
         * 지금 구조에서는 Payment.portonePaymentId에 주문 생성 시 paymentId가 저장되어 있어야
         * webhook paymentId로 Payment를 찾을 수 있음.
         */
        Long orderId = paymentRepository.findOrderIdByPortonePaymentId(request.getPaymentId()).orElse(null);

        if (orderId == null) {
            // AI 플랜 구독 결제(ai-plan- prefix)는 AI 플랜 서비스로 위임
            if (request.getPaymentId() != null && request.getPaymentId()
                    .startsWith(com.eeum.eeum.application.ai.service.AiPlanPaymentCommandExecutor.AI_PLAN_PAYMENT_PREFIX)) {
                aiPlanSubscriptionService.handleWebhook(request.getPaymentId());
                return;
            }
            log.warn("등록되지 않은 paymentId Webhook 수신: paymentId={}",
                    request.getPaymentId());
            return;
        }

        PortOnePaymentInfo externalPayment = recordPortOneFailure(
                OperationFailureCategory.EXTERNAL_API, "PaymentService.handleWebhook.getPayment",
                request.getPaymentId(), "orderId=" + orderId,
                () -> portOnePaymentClient.getPayment(request.getPaymentId()));

        if ("CANCELLED".equalsIgnoreCase(externalPayment.getStatus())) {
            // PG가 이미 취소됐으므로 재호출하지 않는다. Payment·Order·재고·정산은 공통 취소
            // 작업이 짧은 독립 트랜잭션으로 함께 반영한다.
            paymentCancellationService.cancel(orderId, PaymentCancellationTrigger.PORTONE_WEBHOOK,
                    "PortOne 외부 취소 Webhook", true);
            return;
        }
        if ("PARTIAL_CANCELLED".equalsIgnoreCase(externalPayment.getStatus())) {
            operationFailureRecorder.record(
                    OperationFailureCategory.REFUND,
                    "PaymentService.handleWebhook.partialCancel",
                    "order", String.valueOf(orderId),
                    "PARTIAL_CANCEL_NOT_SUPPORTED",
                    "부분 취소는 자동 반영 대상이 아님 — 수동 확인 필요",
                    "paymentId=" + request.getPaymentId() + ", externalStatus=" + externalPayment.getStatus());
            return;
        }

        paymentWebhookProcessor.applyPaidWebhook(orderId, request.getPaymentId(), externalPayment);
    }

    private void validateWebhookSignature(String rawBody, String signature) {
        String secret = portOneProperties.webhookSecret();
        if (!StringUtils.hasText(secret)) {
            // 서버 설정 오류다. 공격자가 반복 유발할 수 있으므로 2단계와 같은 제한 기록을 쓴다.
            log.error("PortOne Webhook Secret이 설정되지 않았습니다.");
            recordWebhookSignatureFailure("Webhook Secret 미설정", null);
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }

        // [1단계] 서명 헤더 자체가 없으면 PortOne이 보낸 요청이 아니다 — 기록 없이 400.
        if (!StringUtils.hasText(signature)) {
            log.warn("서명 헤더 없는 Webhook 수신");
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_MALFORMED);
        }

        String expected = hmacSha256Hex(rawBody, secret);
        String normalizedSignature = normalizeSignature(signature);

        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            // [2단계] 서명은 왔는데 맞지 않는다 — 시크릿 로테이션 사고일 수도, 공격일 수도 있다.
            // 카운터로 전량 집계하고 이력은 구간당 1건만 남긴다.
            recordWebhookSignatureFailure("서명 불일치", rawBody);
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
    }

    private void validatePaymentAmount(
            Order order,
            PortOnePaymentInfo paymentInfo
    ) {
        if (paymentInfo == null) {
            log.warn("결제 검증 실패 — PortOne 조회 결과 없음: orderNumber={}", order.getOrderNumber());
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }

        if (paymentInfo.getAmount() == null
                || paymentInfo.getAmount().compareTo(order.getTotalPrice()) != 0) {
            log.warn("결제 검증 실패 — 금액 불일치: orderNumber={}, 주문금액={}, 실결제금액={}",
                    order.getOrderNumber(), order.getTotalPrice(), paymentInfo.getAmount());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void verifyPaymentWithLock(
            Long accountId,
            PaymentCompleteRequestDto request
    ) {
        Order order = orderRepository
                .findByOrderNumberWithPessimisticLock(request.getOrderNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        Payment payment = paymentRepository
                .findByOrderIdWithPessimisticLock(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("결제 검증 실패 — Payment 상태가 PENDING이 아님: orderNumber={}, paymentStatus={}",
                    order.getOrderNumber(), payment.getStatus());
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }

        // 주문 생성 시 발급해 프론트에 내려준 merchant paymentId와 일치하는지 검증한다.
        // 이 확인 없이 클라이언트가 보낸 paymentId를 신뢰하면, 같은 금액의 타인 결제(paymentId)를
        // 다른 주문에 붙여 결제 완료 처리하는 도용이 가능하다(금액 일치만으로는 막지 못함).
        if (payment.getPortonePaymentId() == null
                || !payment.getPortonePaymentId().equals(request.getPaymentId())) {
            log.warn("결제 검증 실패 — paymentId 불일치: orderNumber={}, 발급된 paymentId={}, 요청 paymentId={}",
                    order.getOrderNumber(), payment.getPortonePaymentId(), request.getPaymentId());
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }

        PortOnePaymentInfo paymentInfo = recordPortOneFailure(
                OperationFailureCategory.EXTERNAL_API,
                "PaymentService.verifyPayment.getPayment",
                request.getPaymentId(),
                "orderNumber=" + order.getOrderNumber(),
                () -> portOnePaymentClient.getPayment(request.getPaymentId()));

        validatePaymentAmount(order, paymentInfo);

         if (!"PAID".equalsIgnoreCase(paymentInfo.getStatus())) {
             log.warn("결제 검증 실패 — PortOne 결제 상태가 PAID가 아님: orderNumber={}, portoneStatus={}",
                     order.getOrderNumber(), paymentInfo.getStatus());
             // 여기서 payment.fail()/expirePendingOrder()를 호출해도, 아래 throw로 본 트랜잭션이
             // 전부 롤백되어 효과가 없다(게다가 order/payment 행에 비관적 락을 쥔 채라 별도 트랜잭션으로
             // 분리하면 같은 행에서 락 대기 데드락이 난다). 결제 미완료 주문의 만료는 PENDING 15분 경과 시
             // OrderExpirationScheduler가 처리하는 것을 안전망으로 사용한다.
             throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
         }

        payment.markAsPaid(paymentInfo.getPgProvider());
        order.markAsPaid();
        ownerRevenueService.recordPaidOrder(order, payment);
        publishPaidEvents(order);

        log.info("결제 검증 완료: orderNumber={}, paymentId={}",
                order.getOrderNumber(), request.getPaymentId());
    }

    // 온라인 결제 완료 시점에 알림 이벤트를 발행한다.
    // - 사장에게 NEW_ORDER (결제가 끝난 주문만 알림)
    // - 고객에게 PAYMENT_COMPLETED
    private void publishPaidEvents(Order order) {
        eventPublisher.publishEvent(new OrderPlacedEvent(
                order.getStore().getAccount().getAccountId(),
                order.getAccount().getName(),
                order.getStore().getName(),
                order.getOrderNumber(),
                order.getOrderId()));

        eventPublisher.publishEvent(new OrderPaidEvent(
                order.getAccount().getAccountId(),
                order.getStore().getName(),
                order.getOrderNumber(),
                order.getTotalPrice(),
                order.getOrderId()));
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

    private String normalizeSignature(String signature) {
        String value = signature.trim();
        if (value.startsWith("sha256=")) {
            return value.substring("sha256=".length());
        }
        return value;
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
