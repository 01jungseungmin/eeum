package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.settlement.enums.PayoutGatewayType;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
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
import org.springframework.util.StringUtils;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "weekly_settlement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_weekly_settlement_period", columnNames = {"store_id", "period_start_at", "period_end_at"}),
        @UniqueConstraint(name = "uk_weekly_settlement_idempotency_key", columnNames = "payout_idempotency_key")
}, indexes = {
        @Index(name = "idx_weekly_settlement_store_period", columnList = "store_id,period_end_at,weekly_settlement_id"),
        @Index(name = "idx_weekly_settlement_claim", columnList = "status,claim_expires_at,weekly_settlement_id")
})
public class WeeklySettlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_settlement_id")
    private Long weeklySettlementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "period_start_at", nullable = false)
    private LocalDateTime periodStartAt;

    @Column(name = "period_end_at", nullable = false)
    private LocalDateTime periodEndAt;

    @Column(name = "payment_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "pg_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal pgFeeAmount;

    @Column(name = "platform_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WeeklySettlementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_gateway", length = 30)
    private PayoutGatewayType payoutGateway;

    @Column(name = "payout_external_id", length = 100)
    private String payoutExternalId;

    @Column(name = "payout_idempotency_key", nullable = false, unique = true, length = 100)
    private String payoutIdempotencyKey;

    @Column(name = "payout_result_status", length = 50)
    private String payoutResultStatus;

    @Column(name = "payout_requested_at")
    private LocalDateTime payoutRequestedAt;

    @Column(name = "payout_completed_at")
    private LocalDateTime payoutCompletedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "claim_token", length = 100)
    private String claimToken;

    @Column(name = "claim_expires_at")
    private LocalDateTime claimExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_completed_by")
    private Account manualCompletedBy;

    @Column(name = "manual_payout_reference", length = 100)
    private String manualPayoutReference;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static WeeklySettlement create(
            Store store,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            String payoutIdempotencyKey
    ) {
        if (store == null || periodStartAt == null || periodEndAt == null
                || !periodStartAt.isBefore(periodEndAt) || !StringUtils.hasText(payoutIdempotencyKey)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        WeeklySettlement settlement = new WeeklySettlement();
        settlement.store = store;
        settlement.periodStartAt = periodStartAt;
        settlement.periodEndAt = periodEndAt;
        settlement.paymentAmount = BigDecimal.ZERO;
        settlement.pgFeeAmount = BigDecimal.ZERO;
        settlement.platformFeeAmount = BigDecimal.ZERO;
        settlement.payoutAmount = BigDecimal.ZERO;
        settlement.status = WeeklySettlementStatus.PAYOUT_PENDING;
        settlement.payoutIdempotencyKey = payoutIdempotencyKey;
        return settlement;
    }

    public void claim(
            String claimToken,
            LocalDateTime claimExpiresAt,
            LocalDateTime requestedAt,
            LocalDateTime now
    ) {
        if (now == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        boolean canClaim = status == WeeklySettlementStatus.PAYOUT_PENDING
                || status == WeeklySettlementStatus.MANUAL_REVIEW_REQUIRED
                || (status == WeeklySettlementStatus.FAILED
                && this.claimExpiresAt != null
                && !this.claimExpiresAt.isAfter(now))
                || (status == WeeklySettlementStatus.PAYOUT_IN_PROGRESS
                && this.claimExpiresAt != null
                && !this.claimExpiresAt.isAfter(now));
        if (!canClaim || !StringUtils.hasText(claimToken) || claimExpiresAt == null
                || requestedAt == null || !claimExpiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = WeeklySettlementStatus.PAYOUT_IN_PROGRESS;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        this.payoutRequestedAt = requestedAt;
    }

    public void requireManualReview(
            String claimToken,
            String failureCode,
            String failureReason,
            LocalDateTime now
    ) {
        validateFailedClaim(claimToken, now);
        this.status = WeeklySettlementStatus.MANUAL_REVIEW_REQUIRED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void completeManually(
            Account completedBy,
            String claimToken,
            String payoutReference,
            LocalDateTime completedAt
    ) {
        validateActiveClaim(claimToken, completedAt);
        if (completedBy == null || !StringUtils.hasText(payoutReference)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = WeeklySettlementStatus.COMPLETED;
        this.payoutGateway = PayoutGatewayType.MANUAL;
        this.manualCompletedBy = completedBy;
        this.manualPayoutReference = payoutReference;
        this.payoutResultStatus = "COMPLETED";
        this.payoutCompletedAt = completedAt;
        this.claimToken = null;
        this.claimExpiresAt = null;
    }

    private void validateActiveClaim(String claimToken, LocalDateTime now) {
        if (status != WeeklySettlementStatus.PAYOUT_IN_PROGRESS
                || this.claimToken == null
                || !this.claimToken.equals(claimToken)
                || claimExpiresAt == null
                || now == null
                || !now.isBefore(claimExpiresAt)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);
        }
    }

    public void markFailed(String claimToken, String failureCode, String failureReason, LocalDateTime now) {
        validateActiveClaim(claimToken, now);
        this.status = WeeklySettlementStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void addRevenue(OwnerRevenue revenue) {
        if (status != WeeklySettlementStatus.PAYOUT_PENDING
                || revenue == null
                || revenue.getStatus() != com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus.SETTLEMENT_PENDING
                || !Objects.equals(store.getStoreId(), revenue.getStore().getStoreId())) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.paymentAmount = this.paymentAmount.add(revenue.getPaymentAmount());
        this.pgFeeAmount = this.pgFeeAmount.add(revenue.getPgFeeAmount());
        this.platformFeeAmount = this.platformFeeAmount.add(revenue.getPlatformFeeAmount());
        this.payoutAmount = this.payoutAmount.add(revenue.getPayoutAmount());
    }

    void removeRevenue(WeeklySettlementItem item) {
        if (item == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        OwnerRevenue revenue = item.getOwnerRevenue();
        if (status != WeeklySettlementStatus.PAYOUT_PENDING
                || item.getWeeklySettlement() == null
                || weeklySettlementId == null
                || !Objects.equals(item.getWeeklySettlement().getWeeklySettlementId(), weeklySettlementId)
                || revenue == null
                || revenue.getStatus() != com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus.SETTLEMENT_PENDING
                || !Objects.equals(store.getStoreId(), revenue.getStore().getStoreId())) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.paymentAmount = this.paymentAmount.subtract(revenue.getPaymentAmount());
        this.pgFeeAmount = this.pgFeeAmount.subtract(revenue.getPgFeeAmount());
        this.platformFeeAmount = this.platformFeeAmount.subtract(revenue.getPlatformFeeAmount());
        this.payoutAmount = this.payoutAmount.subtract(revenue.getPayoutAmount());
    }

    private void validateClaimToken(String claimToken) {
        if (this.claimToken == null || !this.claimToken.equals(claimToken)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);
        }
    }

    private void validateFailedClaim(String claimToken, LocalDateTime now) {
        if (status != WeeklySettlementStatus.FAILED
                || now == null
                || claimExpiresAt == null
                || !now.isBefore(claimExpiresAt)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CLAIM_MISMATCH);
        }
        validateClaimToken(claimToken);
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
