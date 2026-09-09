package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "weekly_settlement_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_weekly_settlement_item_revenue", columnNames = "owner_revenue_id")
}, indexes = {
        @Index(name = "idx_weekly_settlement_item_settlement", columnList = "weekly_settlement_id,weekly_settlement_item_id")
})
public class WeeklySettlementItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_settlement_item_id")
    private Long weeklySettlementItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_settlement_id", nullable = false)
    private WeeklySettlement weeklySettlement;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_revenue_id", nullable = false, unique = true)
    private OwnerRevenue ownerRevenue;

    @Column(name = "payment_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "pg_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal pgFeeAmount;

    @Column(name = "platform_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payoutAmount;

    public static WeeklySettlementItem create(WeeklySettlement settlement, OwnerRevenue revenue) {
        settlement.addRevenue(revenue);
        WeeklySettlementItem item = new WeeklySettlementItem();
        item.weeklySettlement = settlement;
        item.ownerRevenue = revenue;
        item.paymentAmount = revenue.getPaymentAmount();
        item.pgFeeAmount = revenue.getPgFeeAmount();
        item.platformFeeAmount = revenue.getPlatformFeeAmount();
        item.payoutAmount = revenue.getPayoutAmount();
        return item;
    }
}
