package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.ai.dto.response.AiPlanPaymentCancellationPlan;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanPaymentCancellationOperation;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentCancellationOperationRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    private final AiPlanPaymentCancellationOperationRepository cancellationOperationRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final StoreRepository storeRepository;

    // 구독 결제 요청 생성 — store 단위 락 안에서 같은 플랜의 PENDING 결제를 재사용하거나 새로 만든다.
    @Transactional
    public AiPlanPayment requestSubscriptionInTx(Store store, AiPlanType planType) {
        Store lockedStore = lockStore(store.getStoreId());
        // 같은 플랜의 남은 기간도 결제 후 한 달을 이어 붙이는 정책이므로, 활성 구독 자체는
        // 결제 요청을 막는 근거가 아니다. 대신 아직 결제창을 완료하지 않은 요청을 재사용해
        // 더블 클릭이나 새로고침이 여러 PENDING 결제를 만들지 않게 한다.
        return aiPlanPaymentRepository
                .findFirstByStore_StoreIdAndPlanTypeAndStatusOrderByCreatedAtDesc(
                        lockedStore.getStoreId(), planType,
                        com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus.PENDING)
                .orElseGet(() -> {
                    String paymentId = AI_PLAN_PAYMENT_PREFIX + store.getStoreId() + "-"
                            + UUID.randomUUID().toString().substring(0, 8);
                    return aiPlanPaymentRepository.save(
                            AiPlanPayment.createPending(lockedStore, planType, planType.getMonthlyPrice(), paymentId));
                });
    }

    // 결제 검증 + 구독 반영 — 호출부가 aiPlanPayment 락을 잡은 상태에서 호출, 이 메서드가 커밋된 뒤에만 락이 풀린다
    @Transactional
    public void applyPaidSubscriptionInTx(String paymentId, PortOnePaymentInfo info) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        lockStore(payment.getStore().getStoreId());
        if (payment.isPaid()) {
            // 단순 멱등 스킵이 아니라, payment는 PAID인데 구독이 누락된 상태(장애/버그로 인한 불일치)라면 복구한다
            // 결제별로 연결된 구독만 복구한다. 현재 활성 플랜을 비교하면 오래된 BASIC
            // Webhook이 최신 PRO 구독을 BASIC으로 되돌릴 수 있다.
            applySubscriptionIfMissing(payment);
            return;
        }

        // 취소·실패로 종결된 결제는 늦게 도착한 PAID 조회 결과로 되살리지 않는다.
        // 외부 스냅샷은 Redis 락 획득 전에 읽히므로, 실제 상태 전이는 잠근 뒤의 DB 행을 기준으로 한다.
        if (payment.getStatus() != com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus.PENDING) {
            log.warn("[AI-PLAN] 종결된 결제의 PAID 재전송을 무시: paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return;
        }

        // PortOne 조회·취소는 트랜잭션을 시작하기 전에 호출부에서 끝낸다. 여기서는 검증된
        // 응답을 짧은 DB 트랜잭션으로 원장에 반영한다.
        if (!"PAID".equalsIgnoreCase(info.getStatus())) {
            log.warn("[AI-PLAN] 결제 완료 상태가 아님: status={}", info.getStatus());
            throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }
        if (info.getAmount() == null || payment.getAmount().compareTo(info.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        LocalDateTime now = LocalDateTime.now();
        payment.markPaid(now);
        applyPeriod(payment, now);
    }

    /**
     * 남은 기간을 버리지 않는다. 같은 플랜·상위 플랜 결제는 즉시 적용하고 남은 기간 뒤에 한 달을
     * 더한다. 하위 플랜 결제는 상위 권한을 미리 깎지 않도록 현재 기간이 끝난 뒤로 예약한다.
     */
    private void applyPeriod(AiPlanPayment payment, LocalDateTime now) {
        Long storeId = payment.getStore().getStoreId();
        List<AiPlanSubscription> actives = aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(storeId);

        LocalDateTime remainingUntil = actives.stream()
                .map(AiPlanSubscription::getExpiredAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .filter(now::isBefore)
                .orElse(null);

        if (remainingUntil == null) {
            actives.forEach(subscription -> subscription.deactivate(now));
            save(payment, now, now.plusMonths(SUBSCRIPTION_PERIOD_MONTHS), true);
            return;
        }

        boolean downgrade = actives.stream()
                .map(AiPlanSubscription::getPlanType)
                .anyMatch(active -> active.isAtLeast(payment.getPlanType())
                        && active != payment.getPlanType());
        if (downgrade) {
            save(payment, remainingUntil, remainingUntil.plusMonths(SUBSCRIPTION_PERIOD_MONTHS), false);
            log.info("[AI-PLAN] 하위 플랜 결제 — 현재 기간 종료 후로 예약: storeId={}, plan={}, startAt={}",
                    storeId, payment.getPlanType(), remainingUntil);
            return;
        }

        actives.forEach(subscription -> subscription.deactivate(now));
        save(payment, now, remainingUntil.plusMonths(SUBSCRIPTION_PERIOD_MONTHS), true);
        log.info("[AI-PLAN] 구독 반영 완료 — 남은 기간 이어붙임: storeId={}, plan={}, expiredAt={}",
                storeId, payment.getPlanType(), remainingUntil.plusMonths(SUBSCRIPTION_PERIOD_MONTHS));
    }

    private void save(AiPlanPayment payment, LocalDateTime startedAt, LocalDateTime expiredAt, boolean immediate) {
        aiPlanSubscriptionRepository.save(immediate
                ? AiPlanSubscription.createWithPeriod(
                        payment.getStore(), payment, payment.getPlanType(), startedAt, expiredAt)
                : AiPlanSubscription.createReserved(
                        payment.getStore(), payment, payment.getPlanType(), startedAt, expiredAt));
    }

    // payment는 이미 PAID인데 그 플랜의 활성 구독이 없는 상태(장애/버그로 구독 생성이 누락된 경우)를 복구한다.
    // 정상 케이스(구독이 이미 있음)에서는 아무 것도 하지 않는 순수 멱등 스킵.
    private void applySubscriptionIfMissing(AiPlanPayment payment) {
        Long storeId = payment.getStore().getStoreId();
        if (aiPlanSubscriptionRepository.findByPayment_AiPlanPaymentId(payment.getAiPlanPaymentId()).isPresent()) {
            log.info("[AI-PLAN] 이미 반영된 결제 — 멱등 스킵: storeId={}", storeId);
            return;
        }

        log.warn("[AI-PLAN] 결제는 PAID인데 활성 구독이 없어 복구 처리: storeId={}, plan={}, paymentId={}",
                storeId, payment.getPlanType(), payment.getPortonePaymentId());
        LocalDateTime baseTime = payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now();
        var activeSubscriptions = aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(storeId);
        // 연결 컬럼이 도입되기 전에 생성된 구독은 payment가 null일 수 있다. 그 상태에서
        // 오래된 결제의 재전송을 "누락 복구"로 취급하면 최신 구독을 덮어쓴다. 더 최근(또는
        // 같은 시각)의 활성 구독은 보존하고, 연결이 없는 과거 결제는 운영 대사 대상으로 남긴다.
        if (activeSubscriptions.stream().anyMatch(subscription -> subscription.getStartedAt() != null
                && !subscription.getStartedAt().isBefore(baseTime))) {
            log.warn("[AI-PLAN] 연결되지 않은 과거 결제 재전송을 건너뜀: storeId={}, paymentId={}",
                    storeId, payment.getPortonePaymentId());
            return;
        }
        activeSubscriptions.forEach(subscription -> subscription.deactivate(baseTime));
        aiPlanSubscriptionRepository.save(AiPlanSubscription.createWithPeriod(
                payment.getStore(), payment, payment.getPlanType(), baseTime,
                baseTime.plusMonths(SUBSCRIPTION_PERIOD_MONTHS)));
    }

    private Store lockStore(Long storeId) {
        return storeRepository.findByIdWithPessimisticLock(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    /** 외부 PortOne 호출 전에 환불 작업과 멱등키를 먼저 커밋한다. */
    @Transactional
    public AiPlanPaymentCancellationPlan prepareMismatchedPaymentCancellation(String paymentId, java.math.BigDecimal amount) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        AiPlanPaymentCancellationOperation operation = cancellationOperationRepository
                .findByPaymentIdWithPessimisticLock(paymentId).orElse(null);
        if (operation == null) {
            operation = cancellationOperationRepository.save(
                    AiPlanPaymentCancellationOperation.request(payment, amount));
            return new AiPlanPaymentCancellationPlan(paymentId, operation.getRequestedAmount(),
                    operation.getIdempotencyKey(), true);
        }
        if (operation.getStatus() == AiPlanPaymentCancellationStatus.SUCCEEDED
                || operation.getStatus() == AiPlanPaymentCancellationStatus.REQUESTED) {
            return new AiPlanPaymentCancellationPlan(paymentId, operation.getRequestedAmount(),
                    operation.getIdempotencyKey(), false);
        }
        // PENDING은 PortOne 호출 전 중단되었거나 응답을 받기 전 종료된 상태다. 같은
        // idempotency key로 재호출해 PG가 이미 받은 요청이면 그 결과를 돌려받고, 아직
        // 받지 못했으면 안전하게 최초 요청을 수행한다.
        if (operation.getStatus() == AiPlanPaymentCancellationStatus.PENDING) {
            return new AiPlanPaymentCancellationPlan(paymentId, operation.getRequestedAmount(),
                    operation.getIdempotencyKey(), true);
        }
        operation.retry();
        return new AiPlanPaymentCancellationPlan(paymentId, operation.getRequestedAmount(),
                operation.getIdempotencyKey(), true);
    }

    @Transactional
    public void recordMismatchedPaymentCancellationResult(String paymentId, PortOneCancelResult result) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        AiPlanPaymentCancellationOperation operation = cancellationOperationRepository
                .findByPaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS));
        boolean succeeded = result.isSucceeded()
                && result.cancelledAmount() != null
                && operation.getRequestedAmount().compareTo(result.cancelledAmount()) == 0;
        if (succeeded) {
            operation.recordSucceeded(result.cancellationId(), result.cancelledAmount());
            cancelPaymentAndSubscription(payment);
        } else if (result.isPending()) {
            operation.recordRequested(result.cancellationId());
        } else {
            operation.recordFailed(result.cancellationId(), result.cancelledAmount());
        }
    }

    @Transactional
    public void recordMismatchedPaymentCancellationFailure(String paymentId) {
        cancellationOperationRepository.findByPaymentIdWithPessimisticLock(paymentId)
                .ifPresent(operation -> operation.recordFailed(null, null));
    }

    /** REQUESTED 보상 환불의 최종 CANCELLED Webhook을 영속 작업에도 반영한다. */
    @Transactional
    public void confirmMismatchedPaymentCancellation(String paymentId) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        cancellationOperationRepository.findByPaymentIdWithPessimisticLock(paymentId)
                .filter(operation -> operation.getStatus() == AiPlanPaymentCancellationStatus.REQUESTED)
                .ifPresent(operation -> {
                    operation.recordSucceeded(operation.getPgCancellationId(), operation.getRequestedAmount());
                    cancelPaymentAndSubscription(payment);
                });
    }

    /** PG가 외부에서 전액 취소한 AI 결제를 내부 권한과 맞춘다. */
    @Transactional
    public void cancelPaidSubscriptionInTx(String paymentId) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() == com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus.CANCELLED) {
            return;
        }
        cancelPaymentAndSubscription(payment);
    }

    /** 부분 환불은 결제 상태만 기록한다. 유료 권한은 기간 종료까지 유지한다. */
    @Transactional
    public void partiallyCancelPaidSubscriptionInTx(String paymentId) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() == com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus.PARTIALLY_CANCELLED
                || payment.getStatus() == com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus.CANCELLED) {
            return;
        }
        payment.partiallyCancel();
    }

    private void cancelPaymentAndSubscription(AiPlanPayment payment) {
        aiPlanSubscriptionRepository.findByPayment_AiPlanPaymentId(payment.getAiPlanPaymentId())
                .filter(AiPlanSubscription::isActive)
                .ifPresent(subscription -> subscription.deactivate(LocalDateTime.now()));
        payment.cancel();
    }
}
