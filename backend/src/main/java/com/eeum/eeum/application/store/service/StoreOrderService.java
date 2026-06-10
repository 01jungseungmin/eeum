package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.response.StoreOrderResponseDto;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreOrderService {

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        return StoreOrderResponseDto.of(order, orderItems);
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
            return StoreOrderResponseDto.of(order, orderItems);
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