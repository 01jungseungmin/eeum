package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AiPlanSubscriptionService가 Redis 락을 먼저 잡은 뒤 호출하는 DB 작업 전담 Executor.
 * 락 → 이 클래스의 @Transactional 메서드(커밋까지 완료) → 락 해제 순서를 보장하기 위해 별도 빈으로 분리했다
 * (같은 클래스 self-invocation으로는 Spring AOP 트랜잭션 프록시가 동작하지 않음 — AiMessageCommandExecutor와 동일 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanPaymentCommandExecutor {

    public static final String AI_PLAN_PAYMENT_PREFIX = "ai-plan-";
    private static final int SUBSCRIPTION_PERIOD_MONTHS = 1;

    private final AiPlanPaymentRepository aiPlanPaymentRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final PortOnePaymentClient portOnePaymentClient;
    private final AiPlanPaymentFailureRecorder failureRecorder;
    private final OperationFailureRecorder operationFailureRecorder;

    // 구독 결제 요청 생성 — store 단위 락 안에서 "이미 같은 플랜 활성 구독 여부" 체크와 PENDING 결제 생성을 원자적으로 수행
    @Transactional
    public AiPlanPayment requestSubscriptionInTx(Store store, AiPlanType planType) {
        aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(store.getStoreId())
                .filter(subscription -> subscription.getPlanType() == planType)
                .ifPresent(subscription -> {
                    throw new BusinessException(ErrorCode.AI_PLAN_ALREADY_SUBSCRIBED);
                });

        String paymentId = AI_PLAN_PAYMENT_PREFIX + store.getStoreId() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        return aiPlanPaymentRepository.save(
                AiPlanPayment.createPending(store, planType, planType.getMonthlyPrice(), paymentId));
    }

    // 결제 검증 + 구독 반영 — 호출부가 aiPlanPayment 락을 잡은 상태에서 호출, 이 메서드가 커밋된 뒤에만 락이 풀린다
    @Transactional
    public void applyPaidSubscriptionInTx(String paymentId) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.isPaid()) {
            // 단순 멱등 스킵이 아니라, payment는 PAID인데 구독이 누락된 상태(장애/버그로 인한 불일치)라면 복구한다
            applySubscriptionIfMissing(payment);
            return;
        }

        // PortOne 결제 검증 — 금액/상태 불일치 시 플랜 변경 없음
        PortOnePaymentInfo info;
        try {
            info = portOnePaymentClient.getPayment(paymentId);
        } catch (RuntimeException e) {
            // 클라이언트는 이력을 남기지 않으므로 여기서 한 번만 기록한다.
            operationFailureRecorder.record(
                    OperationFailureCategory.EXTERNAL_API,
                    "AiPlanPaymentCommandExecutor.getPayment",
                    "PAYMENT", paymentId, e, "aiPlanPaymentId=" + payment.getAiPlanPaymentId());
            throw e;
        }
        if (!"PAID".equalsIgnoreCase(info.getStatus())) {
            log.warn("[AI-PLAN] 결제 완료 상태가 아님: status={}", info.getStatus());
            throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }
        if (info.getAmount() == null || payment.getAmount().compareTo(info.getAmount()) != 0) {
            // 금액이 불일치하면 구독을 부여하지 않으므로, 실제로 결제된 돈(info.getAmount())은 PortOne에서
            // 자동 취소(환불)한다 — 환불하지 않으면 고객이 결제만 하고 구독도 못 받는 금전 피해가 남는다.
            if (info.getAmount() != null) {
                try {
                    portOnePaymentClient.cancelPayment(paymentId, info.getAmount(),
                            "AI 플랜 결제 금액 불일치 — 자동 환불");
                } catch (Exception e) {
                    log.error("[AI-PLAN] 금액 불일치 자동 환불 실패 — 수동 확인 필요: paymentId={}", paymentId, e);
                    // 여기서 삼킨 예외는 대시보드에서만 보인다 — 고객 돈이 PG에 묶인 채로 남는 건이라
                    // 이력이 없으면 수동 확인 자체가 불가능하다.
                    operationFailureRecorder.record(
                            OperationFailureCategory.REFUND,
                            "AiPlanPaymentCommandExecutor.autoCancel",
                            "PAYMENT", paymentId, e,
                            "결제 금액 불일치 자동 환불 실패, amount=" + info.getAmount());
                }
            }
            // 이 메서드가 예외로 롤백되어도 FAILED 기록은 남아야 하므로 REQUIRES_NEW로 먼저 커밋
            failureRecorder.markFailed(paymentId);
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        LocalDateTime now = LocalDateTime.now();
        payment.markPaid(now);

        // 기존 활성 구독 비활성화 후 새 구독 생성 (업그레이드/다운그레이드 즉시 반영)
        aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(payment.getStore().getStoreId())
                .forEach(subscription -> subscription.deactivate(now));
        aiPlanSubscriptionRepository.save(AiPlanSubscription.createWithPeriod(
                payment.getStore(), payment.getPlanType(), now, now.plusMonths(SUBSCRIPTION_PERIOD_MONTHS)));
        log.info("[AI-PLAN] 구독 반영 완료: storeId={}, plan={}",
                payment.getStore().getStoreId(), payment.getPlanType());
    }

    // payment는 이미 PAID인데 그 플랜의 활성 구독이 없는 상태(장애/버그로 구독 생성이 누락된 경우)를 복구한다.
    // 정상 케이스(구독이 이미 있음)에서는 아무 것도 하지 않는 순수 멱등 스킵.
    private void applySubscriptionIfMissing(AiPlanPayment payment) {
        Long storeId = payment.getStore().getStoreId();
        boolean alreadyHasMatchingSubscription = aiPlanSubscriptionRepository
                .findByStore_StoreIdAndActiveTrue(storeId).stream()
                .anyMatch(subscription -> subscription.getPlanType() == payment.getPlanType());
        if (alreadyHasMatchingSubscription) {
            log.info("[AI-PLAN] 이미 반영된 결제 — 멱등 스킵: storeId={}", storeId);
            return;
        }

        log.warn("[AI-PLAN] 결제는 PAID인데 활성 구독이 없어 복구 처리: storeId={}, plan={}, paymentId={}",
                storeId, payment.getPlanType(), payment.getPortonePaymentId());
        LocalDateTime baseTime = payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now();
        aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(storeId)
                .forEach(subscription -> subscription.deactivate(baseTime));
        aiPlanSubscriptionRepository.save(AiPlanSubscription.createWithPeriod(
                payment.getStore(), payment.getPlanType(), baseTime, baseTime.plusMonths(SUBSCRIPTION_PERIOD_MONTHS)));
    }
}
