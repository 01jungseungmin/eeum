package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.PaymentCompleteRequestDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** PortOne I/O와 분리된 결제 검증의 짧은 DB 트랜잭션 단계. */
@Service
@RequiredArgsConstructor
public class PaymentVerificationProcessor {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerRevenueService ownerRevenueService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long prepare(Long accountId, PaymentCompleteRequestDto request) {
        Order order = lockedOrder(request.getOrderNumber());
        Payment payment = lockedPayment(order.getOrderId());
        validateBeforePortOne(accountId, request, order, payment);
        return order.getOrderId();
    }

    @Transactional
    public void apply(Long accountId, PaymentCompleteRequestDto request, PortOnePaymentInfo paymentInfo) {
        Order order = lockedOrder(request.getOrderNumber());
        Payment payment = lockedPayment(order.getOrderId());
        validateBeforePortOne(accountId, request, order, payment);
        if (payment.getStatus() == PaymentStatus.PAID && order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (paymentInfo == null || paymentInfo.getAmount() == null
                || paymentInfo.getAmount().compareTo(order.getTotalPrice()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (!"PAID".equalsIgnoreCase(paymentInfo.getStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
        payment.markAsPaid(paymentInfo.getPgProvider());
        order.markAsPaid();
        ownerRevenueService.recordPaidOrder(order, payment);
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getStore().getAccount().getAccountId(),
                order.getAccount().getName(), order.getStore().getName(), order.getOrderNumber(), order.getOrderId()));
        eventPublisher.publishEvent(new OrderPaidEvent(order.getAccount().getAccountId(), order.getStore().getName(),
                order.getOrderNumber(), order.getTotalPrice(), order.getOrderId()));
    }

    private Order lockedOrder(String orderNumber) {
        return orderRepository.findByOrderNumberWithPessimisticLock(orderNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
    private Payment lockedPayment(Long orderId) {
        return paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
    private void validateBeforePortOne(Long accountId, PaymentCompleteRequestDto request, Order order, Payment payment) {
        if (!order.getAccount().getAccountId().equals(accountId)) throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        if (!request.getPaymentId().equals(payment.getPortonePaymentId())) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
        if (payment.getStatus() == PaymentStatus.PAID && order.getStatus() == OrderStatus.PAID) return;
        if (order.getStatus() != OrderStatus.PENDING) throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        if (payment.getStatus() != PaymentStatus.PENDING) throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
    }
}
