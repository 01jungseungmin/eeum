package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래완료 처리의 DB 단계만 담당한다.
 *
 * StoreOrderService에서 분리한 이유는 프록시다. 같은 빈 안에서 호출하면 @Transactional이
 * 적용되지 않아 주문 락 안에서 트랜잭션이 열리지 않는다.
 *
 * 거래완료는 현장결제를 PAID로 바꾸고 수익 원장을 만드는 돈이 걸린 전이다.
 * 그래서 취소·환불 경로와 같은 Order → Payment 순서로 비관적 락을 잡고, 상태
 * 검증도 잠근 뒤 다시 읽은 값으로 한다. 잠그지 않은 스냅샷으로 판단하면, 취소가 먼저
 * 커밋된 주문을 거래완료로 되살려 환불된 주문에 지급 원장이 생긴다.
 */
@Service
@RequiredArgsConstructor
public class StoreOrderCompletionProcessor {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerRevenueService ownerRevenueService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void complete(Long ownerId, Long orderId) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Long storeOwnerId = order.getStore().getAccount().getAccountId();
        if (!storeOwnerId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }

        // 상태는 잠근 뒤 읽은 주문으로 판단한다. 취소가 먼저 커밋됐다면 여기서 걸린다.
        if (order.getStatus() != OrderStatus.READY) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getPaymentMethod() == PaymentMethod.CASH_ON_SITE) {
            if (payment.getStatus() != PaymentStatus.NOT_PAID) {
                throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
            }
            payment.markAsPaid("CASH_ON_SITE");
        } else if (payment.getStatus() != PaymentStatus.PAID
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }

        order.complete();

        ownerRevenueService.recordPaidOrder(order, payment);
        ownerRevenueService.markOrderCompleted(order, payment);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getAccount().getAccountId(),
                order.getStore().getName(),
                order.getOrderNumber(),
                "거래완료",
                order.getOrderId()));
    }
}
