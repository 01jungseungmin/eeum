package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 금액 불일치 AI 결제의 환불 요청·확정 사실을 결제와 분리해 영속한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_plan_payment_cancellation_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_plan_payment_cancel_payment", columnNames = "ai_plan_payment_id"),
        @UniqueConstraint(name = "uk_ai_plan_payment_cancel_idempotency", columnNames = "idempotency_key")
})
public class AiPlanPaymentCancellationOperation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_plan_payment_cancellation_operation_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_plan_payment_id", nullable = false)
    private AiPlanPayment payment;

    @Column(name = "requested_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiPlanPaymentCancellationStatus status;

    @Column(name = "pg_cancellation_id", length = 100)
    private String pgCancellationId;

    @Column(name = "pg_cancelled_amount", precision = 10, scale = 2)
    private BigDecimal pgCancelledAmount;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static AiPlanPaymentCancellationOperation request(AiPlanPayment payment, BigDecimal amount) {
        AiPlanPaymentCancellationOperation operation = new AiPlanPaymentCancellationOperation();
        operation.payment = payment;
        operation.requestedAmount = amount;
        operation.idempotencyKey = "ai-plan-refund-" + payment.getAiPlanPaymentId();
        // 외부 호출 전에는 PENDING으로 남긴다. 이 커밋 직후 프로세스가 죽어도 같은
        // 멱등키로 PortOne 호출을 재시도해야 하므로 REQUESTED를 쓰면 안 된다.
        operation.status = AiPlanPaymentCancellationStatus.PENDING;
        return operation;
    }

    public void retry() {
        if (status == AiPlanPaymentCancellationStatus.FAILED) {
            status = AiPlanPaymentCancellationStatus.PENDING;
            pgCancellationId = null;
            pgCancelledAmount = null;
            resolvedAt = null;
        }
    }

    public void recordSucceeded(String cancellationId, BigDecimal cancelledAmount) {
        pgCancellationId = cancellationId;
        pgCancelledAmount = cancelledAmount;
        status = AiPlanPaymentCancellationStatus.SUCCEEDED;
        resolvedAt = LocalDateTime.now();
    }

    public void recordRequested(String cancellationId) {
        pgCancellationId = cancellationId;
        status = AiPlanPaymentCancellationStatus.REQUESTED;
    }

    public void recordFailed(String cancellationId, BigDecimal cancelledAmount) {
        pgCancellationId = cancellationId;
        pgCancelledAmount = cancelledAmount;
        status = AiPlanPaymentCancellationStatus.FAILED;
        resolvedAt = LocalDateTime.now();
    }
}
