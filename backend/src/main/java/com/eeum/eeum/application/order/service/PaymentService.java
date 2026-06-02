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
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
         * TODO: PortOne Webhook 서명 검증 로직 구현 필요
         *
         * 현재는 개발/테스트 단계이므로 rawBody 파싱 후 Webhook 처리를 진행한다.
         * 운영 환경에서는 반드시 PortOne에서 전달한 signature와 rawBody를 이용해
         * 요청 위변조 여부를 검증한 뒤에만 결제 상태를 갱신해야 한다.
         *
         * 구현 시 확인할 내용:
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

        // PortOne 취소 API 호출
        portOnePaymentClient.cancelPayment(payment.getPortonePaymentId(), payment.getAmount(),payment.getRefundReason());
        payment.cancel();
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
            log.info("이미 결제 처리 불가능한 주문 상태: orderId={}, status={}",
                    order.getOrderId(), order.getStatus());
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
        }

        payment.markAsPaid("portone-webhook-test");
        order.markAsPaid();

        log.info("Webhook 결제 완료 처리: orderNumber={}",
                order.getOrderNumber());
    }

    private PaymentWebhookRequestDto parseWebhook(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String paymentId = extractPaymentId(root);

            return new PaymentWebhookRequestDto(paymentId);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
    }

    private String extractPaymentId(JsonNode root) {
        JsonNode direct = root.get("paymentId");
        if (direct != null && direct.isTextual()) {
            return direct.asText();
        }

        JsonNode data = root.get("data");
        if (data != null) {
            JsonNode nested = data.get("paymentId");
            if (nested != null && nested.isTextual()) {
                return nested.asText();
            }
        }

        throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
    }

    private void validateWebhookSignature(String rawBody, String signature) {
        String secret = portOneProperties.webhookSecret();
        if (!StringUtils.hasText(secret)) {
            log.warn("PortOne Webhook Secret이 설정되지 않아 서명 검증을 건너뜁니다. 운영 환경에서는 반드시 설정하세요.");
            return;
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
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }

        if (paymentInfo.getAmount() == null
                || paymentInfo.getAmount().compareTo(order.getTotalPrice()) != 0) {
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
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }

        PortOnePaymentInfo paymentInfo =
                portOnePaymentClient.getPayment(request.getPaymentId());

        validatePaymentAmount(order, paymentInfo);

         if (!"PAID".equalsIgnoreCase(paymentInfo.getStatus())) {
             payment.fail("결제 상태가 PAID가 아닙니다.");
             orderService.expirePendingOrder(order.getOrderId());
             throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
         }

        payment.updatePortonePaymentId(request.getPaymentId());
        payment.markAsPaid("portone-test");
        order.markAsPaid();

        log.info("결제 검증 완료: orderNumber={}, paymentId={}",
                order.getOrderNumber(), request.getPaymentId());
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