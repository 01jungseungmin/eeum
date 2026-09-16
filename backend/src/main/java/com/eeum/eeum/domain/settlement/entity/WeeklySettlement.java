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
    @JoinColumn(name = "claimed_by")
    private Account claimedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_completed_by")
    private Account manualCompletedBy;

    @Column(name = "manual_payout_reference", length = 100)
    private String manualPayoutReference;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 생성 입력 검증.
     *
     * 운영 경로는 마감 경쟁을 피하려고 네이티브 upsert
     * (WeeklySettlementRepository.insertIfAbsent)로 행을 만든다. 그 경로도 이
     * 검증을 거치도록 팩토리에서 떼어 냈다 — 검증이 팩토리 안에만 있으면 실제 생성 경로가
     * 아무 검사도 받지 않는다.
     */
    public static void validateCreation(
            Store store,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            String payoutIdempotencyKey
    ) {
        if (store == null || periodStartAt == null || periodEndAt == null
                || !periodStartAt.isBefore(periodEndAt) || !StringUtils.hasText(payoutIdempotencyKey)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
    }

    public static WeeklySettlement create(
            Store store,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            String payoutIdempotencyKey
    ) {
        validateCreation(store, periodStartAt, periodEndAt, payoutIdempotencyKey);
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
            Account claimedBy,
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
        if (!canClaim || claimedBy == null || !StringUtils.hasText(claimToken) || claimExpiresAt == null
                || requestedAt == null || !claimExpiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = WeeklySettlementStatus.PAYOUT_IN_PROGRESS;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        this.claimedBy = claimedBy;
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
        validateManualCompletion(completedBy, claimToken, payoutReference, completedAt);
        this.status = WeeklySettlementStatus.COMPLETED;
        this.payoutGateway = PayoutGatewayType.MANUAL;
        this.manualCompletedBy = completedBy;
        this.manualPayoutReference = payoutReference;
        this.payoutResultStatus = "COMPLETED";
        this.payoutCompletedAt = completedAt;
        this.claimToken = null;
        this.claimExpiresAt = null;
        this.claimedBy = null;
    }

    public void validateManualCompletion(
            Account completedBy, String claimToken, String payoutReference, LocalDateTime completedAt
    ) {
        validateActiveClaim(claimToken, completedAt);
        if (completedBy == null || claimedBy == null
                || !Objects.equals(claimedBy.getAccountId(), completedBy.getAccountId())
                || !StringUtils.hasText(payoutReference)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
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

    /** 지급 전 원장 조정과 정산 합계 변경을 한 도메인 연산으로 묶는다. */
    public void replaceRevenueAmounts(
            WeeklySettlementItem item,
            BigDecimal paymentAmount,
            BigDecimal pgFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal payoutAmount
    ) {
        if (status != WeeklySettlementStatus.PAYOUT_PENDING || item == null
                || item.getWeeklySettlement() != this) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.paymentAmount = this.paymentAmount.subtract(item.getPaymentAmount()).add(paymentAmount);
        this.pgFeeAmount = this.pgFeeAmount.subtract(item.getPgFeeAmount()).add(pgFeeAmount);
        this.platformFeeAmount = this.platformFeeAmount.subtract(item.getPlatformFeeAmount()).add(platformFeeAmount);
        this.payoutAmount = this.payoutAmount.subtract(item.getPayoutAmount()).add(payoutAmount);
        item.replaceAmounts(paymentAmount, pgFeeAmount, platformFeeAmount, payoutAmount);
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

    /**
     * 지급 직전 대사. 포함된 항목의 합계가 이 정산의 합계와 같은지 확인한다.
     *
     * 합계는 #addRevenue/#removeRevenue의 증분 연산으로 유지된다.
     * 증분이 한 번이라도 어긋나면 실제로 지급해야 할 금액과 다른 돈이 나간다. 그래서
     * 지급을 확정하기 전에 항목 원본에서 다시 더한 값과 맞춰 본다.
     */
    public void reconcileWithItemAmounts(
            BigDecimal paymentSum,
            BigDecimal pgFeeSum,
            BigDecimal platformFeeSum,
            BigDecimal payoutSum
    ) {
        if (paymentSum == null || pgFeeSum == null || platformFeeSum == null || payoutSum == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_AMOUNT_MISMATCH);
        }
        validateAmountSnapshot(paymentSum, pgFeeSum, platformFeeSum, payoutSum);
        if (paymentAmount.compareTo(paymentSum) != 0
                || pgFeeAmount.compareTo(pgFeeSum) != 0
                || platformFeeAmount.compareTo(platformFeeSum) != 0
                || payoutAmount.compareTo(payoutSum) != 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_AMOUNT_MISMATCH);
        }
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
