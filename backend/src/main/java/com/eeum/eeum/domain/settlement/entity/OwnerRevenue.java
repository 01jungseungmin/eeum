package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "settleable_at", nullable = false)
    private LocalDateTime settleableAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    public static OwnerRevenue create(
            Order order,
            Payment payment,
            BigDecimal paymentAmount,
            BigDecimal pgFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal payoutAmount,
            LocalDateTime settleableAt
    ) {
        OwnerRevenue revenue = new OwnerRevenue();
        revenue.order = order;
        revenue.payment = payment;
        revenue.store = order.getStore();
        revenue.paymentAmount = paymentAmount;
        revenue.pgFeeAmount = pgFeeAmount;
        revenue.platformFeeAmount = platformFeeAmount;
        revenue.payoutAmount = payoutAmount;
        revenue.status = OwnerRevenueStatus.ACCRUED;
        revenue.settleableAt = settleableAt;
        return revenue;
    }

    public void markSettlementPending() {
        this.status = OwnerRevenueStatus.SETTLEMENT_PENDING;
    }

    public void markSettled() {
        this.status = OwnerRevenueStatus.SETTLED;
    }

    public void cancel(String reason, LocalDateTime cancelledAt) {
        this.status = OwnerRevenueStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = cancelledAt;
    }
}
