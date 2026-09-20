package com.eeum.eeum.application.order.scheduler;

import com.eeum.eeum.application.order.service.PaymentService;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Webhook 또는 브라우저 복귀가 유실된 온라인 결제를 PortOne 조회로 회수한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final int RECOVERY_DELAY_MINUTES = 5;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 300_000)
    @SchedulerLock(name = "reconcilePendingPayments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void reconcilePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RECOVERY_DELAY_MINUTES);
        for (Payment payment : paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold)) {
            try {
                paymentService.reconcilePendingPayment(payment.getPaymentId());
            } catch (RuntimeException e) {
                log.warn("[PAYMENT] PENDING 결제 대사 실패: paymentId={}", payment.getPaymentId(), e);
            }
        }
    }
}
