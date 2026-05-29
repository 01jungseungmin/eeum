package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.dto.request.PaymentWebhookRequestDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final PortOnePaymentClient portOnePaymentClient;

    @Transactional
    public void verifyPayment(Long accountId, PaymentCompleteRequestDto request) {
        Order order = orderRepository.findByOrderNumber(request.getOrderNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE);
        }


        PortOnePaymentInfo paymentInfo =
                portOnePaymentClient.getPayment(request.getPaymentId());

        validatePaymentAmount(order, paymentInfo);

        if (!"PAID".equalsIgnoreCase(paymentInfo.getStatus())
                && !"paid".equalsIgnoreCase(paymentInfo.getStatus())) {
            payment.fail("결제 상태가 PAID가 아닙니다.");
            orderService.expirePendingOrder(order);
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }

        payment.updatePortonePaymentId(request.getPaymentId());
   //     payment.markAsPaid(paymentInfo.getPgProvider());
        payment.markAsPaid("portone");
        order.markAsPaid();

        log.info("결제 검증 완료: orderNumber={}, paymentId={}",
                order.getOrderNumber(), request.getPaymentId());
    }

    @Transactional
    public void handleWebhook(PaymentWebhookRequestDto request) {
        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            log.warn("paymentId 없는 Webhook 수신");
            return;
        }

        PortOnePaymentInfo paymentInfo =
                portOnePaymentClient.getPayment(request.getPaymentId());

        Payment payment = paymentRepository
                .findByPortonePaymentId(request.getPaymentId())
                .orElse(null);

        if (payment == null) {
            log.warn("등록되지 않은 paymentId Webhook 수신: paymentId={}",
                    request.getPaymentId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("이미 처리된 Webhook: paymentId={}", request.getPaymentId());
            return;
        }

        Order order = payment.getOrder();

        validatePaymentAmount(order, paymentInfo);

        String status = paymentInfo.getStatus();

        if ("PAID".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            payment.markAsPaid(paymentInfo.getPgProvider());
            order.markAsPaid();
            log.info("Webhook 결제 완료 처리: orderNumber={}", order.getOrderNumber());
            return;
        }

        if ("FAILED".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
            payment.fail("PortOne 결제 실패");
            orderService.expirePendingOrder(order);
            log.info("Webhook 결제 실패 처리: orderNumber={}", order.getOrderNumber());
            return;
        }

        if ("CANCELLED".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            payment.cancel();
            orderService.expirePendingOrder(order);
            log.info("Webhook 결제 취소 처리: orderNumber={}", order.getOrderNumber());
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
}