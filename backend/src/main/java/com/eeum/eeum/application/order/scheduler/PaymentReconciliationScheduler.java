package com.eeum.eeum.application.order.scheduler;

import com.eeum.eeum.application.order.service.PaymentService;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Webhook 또는 브라우저 복귀가 유실된 온라인 결제를 PortOne 조회로 회수한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final int RECOVERY_DELAY_MINUTES = 5;
    // PortOne read timeout은 10초다. 단일 scheduler와 10분 ShedLock 안에서 끝나도록
    // 한 실행의 외부 호출을 20건으로 제한한다.
    private static final int RECOVERY_BATCH_SIZE = 20;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 300_000)
    @SchedulerLock(name = "reconcilePendingPayments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void reconcilePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RECOVERY_DELAY_MINUTES);
        List<Payment> candidates = paymentRepository
                .findByStatusAndCreatedAtBeforeOrderByCreatedAtAscPaymentIdAsc(
                        PaymentStatus.PENDING, threshold, PageRequest.of(0, RECOVERY_BATCH_SIZE));
        for (Payment payment : candidates) {
            try {
                paymentService.reconcilePendingPayment(payment.getPaymentId());
            } catch (RuntimeException e) {
                log.warn("[PAYMENT] PENDING 결제 대사 실패: paymentId={}", payment.getPaymentId(), e);
            }
        }
    }
}
