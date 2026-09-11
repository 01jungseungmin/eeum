package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.order.service.PaymentCancellationService;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.application.store.dto.response.StoreOrderResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.*;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreOrderService {

    private final FileStorageService fileStorageService;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisLockService redisLockService;
    private final PaymentCancellationService paymentCancellationService;
    private final OwnerRevenueService ownerRevenueService;
    private final OwnerOrderCancellationAuthorizer cancellationAuthorizer;

    private static final Duration ORDER_LOCK_LEASE_TIME = Duration.ofSeconds(10);

    @Transactional(readOnly = true)
    public StoreOrderResponseDto getStoreOrderDetail(Long accountId, Long orderId) {
        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderId(orderId);
        Payment payment = paymentRepository.findByOrder_OrderId(orderId).orElse(null);

        return StoreOrderResponseDto.of(order, orderItems, payment, fileStorageService::resolveImageUrl);
    }

    @Transactional(readOnly = true)
    public Page<StoreOrderResponseDto> getStoreOrders(Long accountId, OrderStatus status, Pageable pageable) {
        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Page<Order> orders = status != null
                ? orderRepository.findByStore_StoreIdAndStatus(store.getStoreId(), status, pageable)
                : orderRepository.findByStore_StoreId(store.getStoreId(), pageable);

        return orders.map(order -> {
            List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderId(order.getOrderId());
            Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId()).orElse(null);
            return StoreOrderResponseDto.of(order, orderItems, payment, fileStorageService::resolveImageUrl);
        });
    }

    @Transactional
    public void confirmOrder(Long ownerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        validateOwnerOrder(ownerId, order);

        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        validatePaymentCompletedBeforeConfirm(order);

        order.confirm();

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getAccount().getAccountId(),
                order.getStore().getName(),
                order.getOrderNumber(),
                "확정",
                order.getOrderId()));
    }

    @Transactional
    public void readyOrder(Long ownerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        validateOwnerOrder(ownerId, order);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        order.ready();

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getAccount().getAccountId(),
                order.getStore().getName(),
                order.getOrderNumber(),
                "픽업준비완료",
                order.getOrderId()));
    }

    @Transactional
    public void completeOrder(Long ownerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        validateOwnerOrder(ownerId, order);

        if (order.getStatus() != OrderStatus.READY) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        order.complete();

        Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getPaymentMethod() == PaymentMethod.CASH_ON_SITE) {
            if (payment.getStatus() != PaymentStatus.NOT_PAID) {
                throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
            }

            payment.markAsPaid("CASH_ON_SITE");
        } else {
            if (payment.getStatus() != PaymentStatus.PAID) {
                throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
            }
        }

        ownerRevenueService.recordPaidOrder(order, payment);
        ownerRevenueService.markOrderCompleted(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getAccount().getAccountId(),
                order.getStore().getName(),
                order.getOrderNumber(),
                "거래완료",
                order.getOrderId()));
    }

    public void rejectOrder(Long ownerId, Long orderId, String reason) {
        PaymentStatus paymentStatus = cancellationAuthorizer.authorizeRejection(ownerId, orderId);
        if (paymentStatus == PaymentStatus.PAID) {
            paymentCancellationService.cancel(orderId, PaymentCancellationTrigger.OWNER_ORDER_REJECT, reason);
            return;
        }
        redisLockService.executeWithLock(
                LockKeys.order(orderId), ORDER_LOCK_LEASE_TIME, ErrorCode.LOCK_ORDER_FAILED,
                () -> cancellationAuthorizer.rejectWithoutPg(ownerId, orderId, reason));
    }

    public void approveRefund(Long ownerId, Long orderId) {
        String reason = cancellationAuthorizer.authorizeRefundApproval(ownerId, orderId);
        paymentCancellationService.cancel(orderId, PaymentCancellationTrigger.OWNER_REFUND_APPROVAL, reason);
    }

    @Transactional
    public void rejectRefund(Long ownerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOwnerOrder(ownerId, order);
        Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getRefundStatus() != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_REFUND_NOT_REQUESTED);
        }
        payment.rejectRefund();
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getAccount().getAccountId(), order.getStore().getName(),
                order.getOrderNumber(), "환불거절", orderId));
        log.info("환불 거절 처리: orderId={}", orderId);
    }

    private void validateOwnerOrder(Long ownerId, Order order) {
        Long storeOwnerId = order.getStore()
                .getAccount()
                .getAccountId();

        if (!storeOwnerId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
    }

    private void validatePaymentCompletedBeforeConfirm(Order order) {
        Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED));

        if (payment.getPaymentMethod() == PaymentMethod.CASH_ON_SITE) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }
    }

}
