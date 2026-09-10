package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사장 취소 요청의 권한·상태 검증과 PG 없는 주문 종료만 담당한다. */
@Service
@RequiredArgsConstructor
public class OwnerOrderCancellationAuthorizer {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public String authorizeRefundApproval(Long ownerId, Long orderId) {
        Order order = getOwnerOrder(ownerId, orderId);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getRefundStatus() != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_REFUND_NOT_REQUESTED);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
        }
        return payment.getRefundReason();
    }

    @Transactional(readOnly = true)
    public PaymentStatus authorizeRejection(Long ownerId, Long orderId) {
        Order order = getOwnerOrder(ownerId, orderId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        return paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
                .getStatus();
    }

    @Transactional
    public void rejectWithoutPg(Long ownerId, Long orderId, String reason) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOwner(ownerId, order);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
        }
        orderService.restoreStockForOrder(orderId);
        if (payment.getStatus() == PaymentStatus.PENDING) payment.cancel();
        order.cancel(reason);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getAccount().getAccountId(),
                order.getStore().getName(), order.getOrderNumber(), "거절", orderId));
    }

    private Order getOwnerOrder(Long ownerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOwner(ownerId, order);
        return order;
    }

    private void validateOwner(Long ownerId, Order order) {
        if (!order.getStore().getAccount().getAccountId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
    }
}
