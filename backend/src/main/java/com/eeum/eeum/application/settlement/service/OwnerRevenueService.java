package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 결제만 사장 매출 원장으로 연결한다. AI 플랜 결제는 Order가 없으므로 이 진입점에 들어올 수 없다.
 */
@Service
@RequiredArgsConstructor
public class OwnerRevenueService {

    private static final BigDecimal ZERO_FEE = BigDecimal.ZERO;

    private final OwnerRevenueRepository ownerRevenueRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final WeeklySettlementItemRepository weeklySettlementItemRepository;

    @Transactional
    public OwnerRevenue recordPaidOrder(Order order, Payment payment) {
        if (order == null || payment == null || payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }

        return ownerRevenueRepository.findByOrder_OrderId(order.getOrderId())
                .orElseGet(() -> ownerRevenueRepository.save(OwnerRevenue.create(
                        order,
                        payment,
                        payment.getAmount(),
                        ZERO_FEE,
                        ZERO_FEE,
                        payment.getAmount()
                )));
    }

    @Transactional
    public void markOrderCompleted(Order order) {
        if (order == null || order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        OwnerRevenue revenue = ownerRevenueRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        revenue.markSettleableAtFromCompletedOrder();
    }

    /**
     * 전액 취소를 원장에 반영한다. 항목이 없는 ACCRUED 원장은 원장만 잠그며,
     * 항목이 있으면 WeeklySettlement → OwnerRevenue 순서로 현재 행을 잠근다.
     */
    @Transactional
    public void cancelBeforePayout(Long orderId, String reason, LocalDateTime cancelledAt) {
        OwnerRevenue snapshot = ownerRevenueRepository.findByOrder_OrderId(orderId)
                .orElse(null);
        if (snapshot == null) {
            return;
        }

        WeeklySettlementItem snapshotItem = weeklySettlementItemRepository
                .findByOwnerRevenue_OwnerRevenueId(snapshot.getOwnerRevenueId())
                .orElse(null);
        if (snapshotItem == null) {
            OwnerRevenue revenue = ownerRevenueRepository.findByOrderIdWithPessimisticLock(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
            revenue.cancel(reason, cancelledAt);
            return;
        }

        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(
                        snapshotItem.getWeeklySettlement().getWeeklySettlementId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        OwnerRevenue revenue = ownerRevenueRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        WeeklySettlementItem item = weeklySettlementItemRepository
                .findByOwnerRevenue_OwnerRevenueId(revenue.getOwnerRevenueId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        if (!item.getWeeklySettlement().getWeeklySettlementId().equals(settlement.getWeeklySettlementId())) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        revenue.cancelBeforePayout(item, reason, cancelledAt);
        weeklySettlementItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public void assertCancellableBeforePayout(Long orderId) {
        OwnerRevenue revenue = ownerRevenueRepository.findByOrder_OrderId(orderId).orElse(null);
        if (revenue != null && revenue.getStatus() != OwnerRevenueStatus.ACCRUED
                && revenue.getStatus() != OwnerRevenueStatus.SETTLEMENT_PENDING) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
    }
}
