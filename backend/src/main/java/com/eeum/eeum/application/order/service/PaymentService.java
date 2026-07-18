package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.dto.request.PaymentWebhookRequestDto;
import com.eeum.eeum.application.order.dto.request.RefundRequestDto;
import com.eeum.eeum.application.order.dto.response.PaymentResponseDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
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
import java.util.HexFormat;

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
    private final com.eeum.eeum.application.ai.service.AiPlanSubscriptionService aiPlanSubscriptionService;

    private static final Duration PAYMENT_LOCK_LEASE_TIME = Duration.ofSeconds(10);

    @Transactional
    public void verifyPayment(Long accountId, PaymentCompleteRequestDto request) {
        redisLockService.executeWithLock(
                LockKeys.orderNumber(request.getOrderNumber()),
                PAYMENT_LOCK_LEASE_TIME,
                ErrorCode.LOCK_PAYMENT_FAILED,
                () -> verifyPaymentWithLock(accountId, request)
        );
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (rawBody == null || rawBody.isBlank()) {
            log.warn("빈 Webhook body 수신");
            return;
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

        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            log.warn("paymentId 없는 Webhook 수신");
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

    @Transactional
    public void cancelPayment(Long accountId, Long paymentId) {
        Payment payment = paymentRepository
                .findByOrder_Account_AccountIdAndPaymentId(accountId, paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException(ErrorCode.PAYMENT_INVALID_STATUS);
        }

        String reason = StringUtils.hasText(payment.getRefundReason())
                ? payment.getRefundReason()
                : "고객 요청 취소";

        // PortOne 취소 API 호출
        portOnePaymentClient.cancelPayment(payment.getPortonePaymentId(), payment.getAmount(), reason);

        payment.cancel();

        // 결제 취소로 끝내지 않고 주문 상태 전이(CANCELLED)와 재고 복원까지 함께 처리한다 —
        // 누락하면 주문이 PAID로 남아 사장 화면에 유효 주문으로 노출되고 차감된 재고가 영구 미복원된다.
        orderService.cancelPaidOrder(payment.getOrder().getOrderId());
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
        Payment foundPayment = paymentRepository
                .findByPortonePaymentId(request.getPaymentId())
                .orElse(null);

        if (foundPayment == null) {
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

        Long orderId = foundPayment.getOrder().getOrderId();

        /*
         * 락 순서 통일:
         * 1. Order PESSIMISTIC_WRITE
         * 2. Payment PESSIMISTIC_WRITE
         */
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Payment payment = paymentRepository
                .findByOrderIdWithPessimisticLock(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("이미 처리된 Webhook: paymentId={}", request.getPaymentId());
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            // 주문이 이미 만료/취소됐는데 PortOne에는 결제가 실제로 PAID면(만료 직전 결제 + 웹훅 지연)
            // 고객 돈이 PG에 묶이므로 자동 취소(환불)로 보상한다. 웹훅 재시도 시에는 PortOne 상태가
            // 이미 CANCELLED이므로 이 분기를 다시 타지 않아 이중 환불되지 않는다.
            PortOnePaymentInfo settledInfo = portOnePaymentClient.getPayment(request.getPaymentId());
            if ("PAID".equalsIgnoreCase(settledInfo.getStatus())) {
                log.warn("만료/취소 주문에 결제 완료 Webhook 수신 — 자동 환불: orderId={}, status={}",
                        order.getOrderId(), order.getStatus());
                portOnePaymentClient.cancelPayment(request.getPaymentId(), settledInfo.getAmount(),
                        "주문 만료 후 결제 완료 — 자동 환불");
            } else {
                log.info("이미 결제 처리 불가능한 주문 상태: orderId={}, status={}",
                        order.getOrderId(), order.getStatus());
            }
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("이미 결제 처리 불가능한 결제 상태: paymentId={}, status={}",
                    payment.getPaymentId(), payment.getStatus());
            return;
        }


        PortOnePaymentInfo paymentInfo =
                 portOnePaymentClient.getPayment(request.getPaymentId());

         validatePaymentAmount(order, paymentInfo);

         if ("PAID".equalsIgnoreCase(paymentInfo.getStatus())) {
             payment.markAsPaid(paymentInfo.getPgProvider());
             order.markAsPaid();
             publishPaidEvents(order);
        }else {
             log.warn("결제 완료 상태가 아닌 Webhook 수신: paymentId={}, status={}",
                     payment.getPortonePaymentId(),
                     paymentInfo.getStatus());
         }

        log.info("Webhook 결제 완료 처리: orderNumber={}",
                order.getOrderNumber());
    }

    private void validateWebhookSignature(String rawBody, String signature) {
        String secret = portOneProperties.webhookSecret();
        if (!StringUtils.hasText(secret)) {
            log.error("PortOne Webhook Secret이 설정되지 않았습니다.");
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }

        if (!StringUtils.hasText(signature)) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }

        String expected = hmacSha256Hex(rawBody, secret);
        String normalizedSignature = normalizeSignature(signature);

        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
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

        PortOnePaymentInfo paymentInfo =
                portOnePaymentClient.getPayment(request.getPaymentId());

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

    private String normalizeSignature(String signature) {
        String value = signature.trim();
        if (value.startsWith("sha256=")) {
            return value.substring("sha256=".length());
        }
        return value;
    }

    private PaymentWebhookRequestDto parseWebhookBody(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentWebhookRequestDto.class);
        } catch (JsonProcessingException e) {
            log.warn("Webhook body 파싱 실패: rawBody={}", rawBody);
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
    }
}