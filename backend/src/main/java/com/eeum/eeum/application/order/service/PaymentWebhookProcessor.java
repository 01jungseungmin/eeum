package com.eeum.eeum.application.order.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class PaymentWebhookProcessor {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerRevenueService ownerRevenueService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void applyPaidWebhook(Long orderId, String paymentId, PortOnePaymentInfo paymentInfo) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() == PaymentStatus.PAID) return;
        if (order.getStatus() != OrderStatus.PENDING || payment.getStatus() != PaymentStatus.PENDING) return;
        if (paymentInfo.getAmount() == null || paymentInfo.getAmount().compareTo(order.getTotalPrice()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (!"PAID".equalsIgnoreCase(paymentInfo.getStatus())) return;
        payment.markAsPaid(paymentInfo.getPgProvider());
        order.markAsPaid();
        ownerRevenueService.recordPaidOrder(order, payment);
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getStore().getAccount().getAccountId(),
                order.getAccount().getName(), order.getStore().getName(), order.getOrderNumber(), orderId));
        eventPublisher.publishEvent(new OrderPaidEvent(order.getAccount().getAccountId(), order.getStore().getName(),
                order.getOrderNumber(), order.getTotalPrice(), orderId));
        log.info("Webhook 결제 완료 처리: paymentId={}, orderId={}", paymentId, orderId);
    }
}
