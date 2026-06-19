package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "portone_payment_id", unique = true, length = 100)
    private String portonePaymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_provider", length = 50)
    private String pgProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 20)
    private RefundStatus refundStatus;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    public static Payment create(
            Order order,
            Account account,
            String portonePaymentId,
            String idempotencyKey,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus
    ) {
        Payment payment = new Payment();
        payment.order = order;
        payment.account = account;
        payment.portonePaymentId = portonePaymentId;
        payment.idempotencyKey = idempotencyKey;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.paymentMethod = paymentMethod;
        payment.status = paymentStatus;
        return payment;
    }

    public void updatePortonePaymentId(String portonePaymentId) {
        this.portonePaymentId = portonePaymentId;
    }

    public void markAsPaid(String pgProvider) {
        this.status = PaymentStatus.PAID;
        this.pgProvider = pgProvider;
        this.paidAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }
    public void requestRefund(String reason) {
        this.refundStatus = RefundStatus.REQUESTED;
        this.refundReason = reason;
    }

    public void completeRefund() {
        this.refundStatus = RefundStatus.APPROVED;
        this.refundedAt = LocalDateTime.now();
        this.status = PaymentStatus.REFUNDED;
    }

    public void rejectRefund() {
        this.refundStatus = RefundStatus.REJECTED;
    }
}