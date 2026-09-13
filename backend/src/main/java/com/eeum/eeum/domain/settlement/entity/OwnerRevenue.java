package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "owner_revenue", uniqueConstraints = {
        @UniqueConstraint(name = "uk_owner_revenue_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_owner_revenue_payment", columnNames = "payment_id")
}, indexes = {
        @Index(name = "idx_owner_revenue_settleable", columnList = "status,settleable_at,owner_revenue_id")
})
public class OwnerRevenue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owner_revenue_id")
    private Long ownerRevenueId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "payment_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "pg_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal pgFeeAmount;

    @Column(name = "platform_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OwnerRevenueStatus status;

    @Column(name = "settleable_at")
    private LocalDateTime settleableAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** 기간 밖 누락 원장을 운영 수습 대기열에 한 번만 기록한 시각. */
    @Column(name = "late_settlement_reported_at")
    private LocalDateTime lateSettlementReportedAt;

    public static OwnerRevenue create(
            Order order,
            Payment payment,
            BigDecimal paymentAmount,
            BigDecimal pgFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal payoutAmount
    ) {
        if (order == null || payment == null
                || payment.getStatus() != PaymentStatus.PAID
                || payment.getOrder() == null
                || order.getOrderId() == null
                || payment.getOrder().getOrderId() == null
                || !Objects.equals(order.getOrderId(), payment.getOrder().getOrderId())
                || paymentAmount == null
                || payment.getAmount() == null
                || payment.getAmount().compareTo(paymentAmount) != 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        validateAmountSnapshot(paymentAmount, pgFeeAmount, platformFeeAmount, payoutAmount);
        OwnerRevenue revenue = new OwnerRevenue();
        revenue.order = order;
        revenue.payment = payment;
        revenue.store = order.getStore();
        revenue.paymentAmount = paymentAmount;
        revenue.pgFeeAmount = pgFeeAmount;
        revenue.platformFeeAmount = platformFeeAmount;
        revenue.payoutAmount = payoutAmount;
        revenue.status = OwnerRevenueStatus.ACCRUED;
        return revenue;
    }

    // 주문 완료 전에는 지급 가능 시각을 만들 수 없다. 결제 완료 원장은 이 값 없이 먼저 생성된다.
    public void markSettleableAtFromCompletedOrder() {
        if (status != OwnerRevenueStatus.ACCRUED
                || settleableAt != null
                || order.getStatus() != OrderStatus.COMPLETED
                || order.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.settleableAt = order.getCompletedAt().plusDays(7);
    }

    public boolean markLateSettlementReported(LocalDateTime periodStartAt, LocalDateTime now) {
        if (status != OwnerRevenueStatus.ACCRUED || settleableAt == null
                || !settleableAt.isBefore(periodStartAt) || lateSettlementReportedAt != null) {
            return false;
        }
        lateSettlementReportedAt = now;
        return true;
    }

    public void markSettlementPending(LocalDateTime now) {
        if (status != OwnerRevenueStatus.ACCRUED
                || settleableAt == null
                || now == null
                || !settleableAt.isBefore(now)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = OwnerRevenueStatus.SETTLEMENT_PENDING;
    }

    public void markSettled() {
        if (status != OwnerRevenueStatus.SETTLEMENT_PENDING) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = OwnerRevenueStatus.SETTLED;
    }

    public void cancel(String reason, LocalDateTime cancelledAt) {
        if (status != OwnerRevenueStatus.ACCRUED || cancelledAt == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = OwnerRevenueStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = cancelledAt;
    }

    // 이미 정산 항목에 포함된 원장은 주간 정산 합계 차감과 같은 트랜잭션에서만 취소한다.
    public void cancelBeforePayout(
            WeeklySettlementItem settlementItem,
            String reason,
            LocalDateTime cancelledAt
    ) {
        if (status != OwnerRevenueStatus.SETTLEMENT_PENDING
                || settlementItem == null
                || settlementItem.getOwnerRevenue() == null
                || settlementItem.getWeeklySettlement() == null
                || ownerRevenueId == null
                || !Objects.equals(settlementItem.getOwnerRevenue().getOwnerRevenueId(), ownerRevenueId)
                || cancelledAt == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        settlementItem.getWeeklySettlement().removeRevenue(settlementItem);
        this.status = OwnerRevenueStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = cancelledAt;
    }

    private static void validateAmountSnapshot(
            BigDecimal paymentAmount,
            BigDecimal pgFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal payoutAmount
    ) {
        if (paymentAmount == null
                || pgFeeAmount == null
                || platformFeeAmount == null
                || payoutAmount == null
                || paymentAmount.signum() < 0
                || pgFeeAmount.signum() < 0
                || platformFeeAmount.signum() < 0
                || payoutAmount.signum() < 0
                || paymentAmount.subtract(pgFeeAmount).subtract(platformFeeAmount)
                .compareTo(payoutAmount) != 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_AMOUNT);
        }
    }
}
