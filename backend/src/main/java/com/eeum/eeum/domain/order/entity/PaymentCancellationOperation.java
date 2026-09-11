package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 전액 취소 작업 이력.
 *
 * <p><b>이 엔티티가 있는 이유.</b> PortOne 취소는 성공하면 되돌릴 수 없는데, 그 뒤의
 * 내부 반영(Payment·Order·재고·정산 원장)은 실패할 수 있다. 전부 한 트랜잭션에 두면
 * 내부 실패가 롤백을 일으켜 "고객은 환불받았는데 내부에는 아무 흔적이 없는" 상태가 된다.
 * 그래서 외부 호출 전에 이 행을 커밋해 두고, 단계마다 상태를 전이시킨다.
 *
 * <p>주문당 한 건이다. 네 진입점(고객 취소·환불 승인·주문 거절·외부 Webhook)이 모두
 * 이 행을 공유하므로, 재시도와 중복 요청이 같은 행에 수렴한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_cancellation_operation",
        uniqueConstraints = {
                // 주문당 전액 취소는 한 번뿐이다. 중복 요청이 새 작업을 만들면
                // PG 취소가 두 번 호출된다.
                @UniqueConstraint(
                        name = "uk_payment_cancellation_operation_order",
                        columnNames = "order_id")
        },
        indexes = {
                // 운영자가 수습해야 할 건을 찾는 경로
                @Index(name = "idx_payment_cancellation_operation_status",
                        columnList = "status, created_at")
        }
)
public class PaymentCancellationOperation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_cancellation_operation_id")
    private Long paymentCancellationOperationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private PaymentCancellationTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentCancellationStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    // 취소를 요청한 금액. PortOne 응답의 취소 금액과 대조한다.
    @Column(name = "requested_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal requestedAmount;

    // PortOne 취소 식별자. 재조회·대사의 근거다.
    @Column(name = "pg_cancellation_id", length = 100)
    private String pgCancellationId;

    // PortOne이 돌려준 취소 상태 원문. SUCCEEDED가 아니면 완료로 확정하지 않는다.
    @Column(name = "pg_status", length = 30)
    private String pgStatus;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "pg_cancelled_at")
    private LocalDateTime pgCancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ===================== 정적 팩토리 메서드 =====================

    public static PaymentCancellationOperation start(
            Order order,
            Payment payment,
            PaymentCancellationTrigger triggerType,
            String reason,
            BigDecimal requestedAmount
    ) {
        if (order == null || payment == null || triggerType == null
                || requestedAmount == null || requestedAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);
        }
        PaymentCancellationOperation operation = new PaymentCancellationOperation();
        operation.order = order;
        operation.payment = payment;
        operation.triggerType = triggerType;
        operation.reason = reason;
        operation.requestedAmount = requestedAmount;
        operation.status = PaymentCancellationStatus.PENDING;
        return operation;
    }

    // ===================== 상태 전이 =====================

    /**
     * 외부 호출 직전. 이 상태로 커밋한 뒤에 PortOne을 호출해야, 응답을 받지 못한 채
     * 프로세스가 죽어도 "요청했을 수 있다"는 사실이 남는다.
     */
    public void markPgRequested(PaymentCancellationTrigger triggerType, String reason, LocalDateTime now) {
        if (status != PaymentCancellationStatus.PENDING
                && status != PaymentCancellationStatus.PG_CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);
        }
        this.triggerType = triggerType;
        this.reason = reason;
        this.status = PaymentCancellationStatus.PG_CANCEL_REQUESTED;
        this.requestedAt = now;
        this.failureCode = null;
        this.failureReason = null;
    }

    /** PortOne이 SUCCEEDED로 확정한 경우만 호출한다. */
    public void markPgCancelled(String pgCancellationId, String pgStatus, LocalDateTime now) {
        if (status != PaymentCancellationStatus.PG_CANCEL_REQUESTED
                && status != PaymentCancellationStatus.PG_CANCELLED) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);
        }
        this.status = PaymentCancellationStatus.PG_CANCELLED;
        this.pgCancellationId = pgCancellationId;
        this.pgStatus = pgStatus;
        this.pgCancelledAt = now;
    }

    public void markCompleted(LocalDateTime now) {
        if (status != PaymentCancellationStatus.PG_CANCELLED) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);
        }
        this.status = PaymentCancellationStatus.COMPLETED;
        this.completedAt = now;
    }

    /**
     * PG 호출 자체가 실패한 경우. 돈이 움직이지 않았으므로 다시 시도할 수 있는 상태
     * ({@code PENDING})로 되돌리고 실패 사유만 남긴다.
     */
    public void markPgFailed(String failureCode, String failureReason) {
        if (status != PaymentCancellationStatus.PG_CANCEL_REQUESTED) {
            return;
        }
        this.status = PaymentCancellationStatus.PENDING;
        this.failureCode = failureCode;
        this.failureReason = truncate(failureReason);
    }

    /**
     * 사람이 확인해야 하는 상태로 격리한다.
     *
     * <p>PG는 취소됐는데 내부 반영이 실패했거나, PortOne이 {@code REQUESTED}만 돌려줘
     * 최종 상태가 확정되지 않은 경우다. 어느 쪽이든 자동 처리로 수렴시키지 않는다.
     */
    public void requireManualReview(String failureCode, String failureReason) {
        if (status == PaymentCancellationStatus.COMPLETED) {
            return;
        }
        this.status = PaymentCancellationStatus.MANUAL_REVIEW_REQUIRED;
        this.failureCode = failureCode;
        this.failureReason = truncate(failureReason);
    }

    public void recordPgStatus(String pgCancellationId, String pgStatus) {
        this.pgCancellationId = pgCancellationId;
        this.pgStatus = pgStatus;
    }

    // ===================== 조회 =====================

    public boolean isCompleted() {
        return status == PaymentCancellationStatus.COMPLETED;
    }

    public boolean isManualReviewRequired() {
        return status == PaymentCancellationStatus.MANUAL_REVIEW_REQUIRED;
    }

    /** PG 취소가 이미 확정된 작업인지 — 재시도 시 외부를 다시 호출하지 않기 위해 본다. */
    public boolean isPgCancelled() {
        return status == PaymentCancellationStatus.PG_CANCELLED;
    }

    public boolean isPgOutcomeUnknown() {
        return status == PaymentCancellationStatus.PG_CANCEL_REQUESTED;
    }

    /**
     * PG 요청 응답을 받지 못한 작업이 복구 유예 시간을 넘겼는지 판별한다.
     *
     * <p>유예 중에는 선행 요청이 아직 PortOne 응답을 처리하고 있을 수 있으므로 상태를
     * 바꾸지 않는다. 유예가 지나도록 확정되지 않은 경우에만 운영 수습 대기열로 넘긴다.
     */
    public boolean isPgOutcomeUnknownFor(Duration gracePeriod, LocalDateTime now) {
        return isPgOutcomeUnknown()
                && requestedAt != null
                && !requestedAt.plus(gracePeriod).isAfter(now);
    }

    /** PG 요청 결과를 잃어버린 오래된 작업만 수동 검토로 전환한다. */
    public boolean requireManualReviewForUnknownPg(
            Duration gracePeriod,
            LocalDateTime now,
            String failureCode,
            String failureReason
    ) {
        if (!isPgOutcomeUnknownFor(gracePeriod, now)) {
            return false;
        }
        requireManualReview(failureCode, failureReason);
        return true;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
