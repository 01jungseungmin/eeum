package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// AI 플랜 구독 결제 — 일반 상품 주문 결제와 분리해 관리 (paymentId prefix: ai-plan-)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_plan_payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_plan_payment_portone_id", columnNames = "portone_payment_id")
})
public class AiPlanPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_plan_payment_id")
    private Long aiPlanPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private AiPlanType planType;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // PortOne 결제 ID — Webhook 매칭 키 (DB unique로 중복 반영 방지)
    @Column(name = "portone_payment_id", nullable = false, length = 100)
    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiPlanPaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public static AiPlanPayment createPending(Store store, AiPlanType planType, BigDecimal amount, String portonePaymentId) {
        AiPlanPayment payment = new AiPlanPayment();
        payment.store = store;
        payment.planType = planType;
        payment.amount = amount;
        payment.portonePaymentId = portonePaymentId;
        payment.status = AiPlanPaymentStatus.PENDING;
        return payment;
    }

    public void markPaid(LocalDateTime paidAt) {
        this.status = AiPlanPaymentStatus.PAID;
        this.paidAt = paidAt;
    }

    // PENDING일 때만 FAILED로 전이 — 만료 스케줄러와 결제 완료가 경합할 때 이미 PAID된 결제를
    // FAILED로 덮어써 "결제됐고 구독도 있는데 기록은 FAILED"가 되는 불일치를 방지한다.
    public void markFailed() {
        if (this.status != AiPlanPaymentStatus.PENDING) {
            return;
        }
        this.status = AiPlanPaymentStatus.FAILED;
    }

    public boolean isPaid() {
        return this.status == AiPlanPaymentStatus.PAID;
    }
}
