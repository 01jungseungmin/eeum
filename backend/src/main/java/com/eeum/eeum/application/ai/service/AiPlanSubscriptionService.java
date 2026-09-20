package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.ai.dto.response.AiPlanPaymentCancellationPlan;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * AI 플랜 구독·결제 연동. 결제 검증 전에는 플랜을 바꾸지 않고, Webhook·검증은 멱등 처리한다.
 *
 * 공개 메서드에 Transactional을 걸지 않는다. 락을 먼저 잡고, DB 쓰기는
 * AiPlanPaymentCommandExecutor의 트랜잭션이 커밋까지 마친 뒤에 락을 푼다.
 * 과거에는 락이 트랜잭션 안에서 풀려 커밋 전에 틈이 생겼고, 그 틈으로 Webhook과 클라이언트
 * 리다이렉트가 동시 진입해 같은 결제에 활성 구독이 2개 생겼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanSubscriptionService {

    private static final Duration PAYMENT_LOCK_LEASE = Duration.ofSeconds(10);
    private static final Duration REQUEST_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    private final AiPlanPaymentRepository aiPlanPaymentRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final AiPlanPaymentCommandExecutor paymentCommandExecutor;
    private final AiPlanPaymentFailureRecorder failureRecorder;
    private final RedisLockService redisLockService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final OperationFailureRecorder operationFailureRecorder;

    // 구독 취소 시 즉시 FREE로 내릴지 (기본: 결제 기간 종료일까지 유지)
    @Value("${ai.plan.cancel-immediately:false}")
    private boolean cancelImmediately;

    // 구독 결제 요청 생성 — 프론트가 반환된 paymentId로 PortOne 결제창을 연다.
    // store 단위 락으로 "이미 같은 플랜 활성 구독 여부" 체크~PENDING 결제 생성을 원자적으로 묶어
    // 더블클릭/멀티탭으로 인한 중복 결제 요청을 막는다 (Executor가 커밋한 뒤에만 락 해제).
    public AiPlanSubscribeResponseDto requestSubscription(Long ownerId, AiPlanSubscribeRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanType planType = request.getPlanType();
        if (planType == null || planType == AiPlanType.FREE) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "구독할 플랜은 BASIC 또는 PRO여야 합니다");
        }

        AiPlanPayment payment = redisLockService.executeWithLock(
                LockKeys.aiPlanSubscriptionRequest(store.getStoreId()),
                REQUEST_LOCK_LEASE,
                () -> paymentCommandExecutor.requestSubscriptionInTx(store, planType));
        return new AiPlanSubscribeResponseDto(
                payment.getPortonePaymentId(), planType, payment.getAmount(), payment.getStatus());
    }

    // 결제 완료 검증 (클라이언트 리다이렉트 경로) — 타 사장 결제 접근 차단.
    // 소유권 확인은 읽기 전용이라 락/TX 없이 진행하고, 실제 반영은 applyPaidSubscription에 위임한다.
    public AiPlanSubscribeResponseDto completePayment(Long ownerId, String paymentId) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.AI_FORBIDDEN);
        }
        applyPaidSubscription(paymentId);
        AiPlanPayment refreshed = aiPlanPaymentRepository.findByPortonePaymentId(paymentId).orElseThrow();
        if (!refreshed.isPaid()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }
        return new AiPlanSubscribeResponseDto(
                paymentId, refreshed.getPlanType(), refreshed.getAmount(), refreshed.getStatus());
    }

    // Webhook 경로 — PaymentService가 ai-plan- prefix 결제를 위임
    public void handleWebhook(String paymentId) {
        if (aiPlanPaymentRepository.findByPortonePaymentId(paymentId).isEmpty()) {
            log.warn("[AI-PLAN] 등록되지 않은 AI 플랜 결제 Webhook: 무시");
            return;
        }
        PortOnePaymentInfo paymentInfo = portOnePaymentClient.getPayment(paymentId);
        if ("CANCELLED".equalsIgnoreCase(paymentInfo.getStatus())) {
            paymentCommandExecutor.confirmMismatchedPaymentCancellation(paymentId);
            redisLockService.executeWithLock(
                    LockKeys.aiPlanPayment(paymentId), PAYMENT_LOCK_LEASE,
                    () -> paymentCommandExecutor.cancelPaidSubscriptionInTx(paymentId));
            return;
        }
        if ("PARTIAL_CANCELLED".equalsIgnoreCase(paymentInfo.getStatus())) {
            operationFailureRecorder.record(
                    OperationFailureCategory.REFUND, "AiPlanSubscriptionService.handleWebhook",
                    "PAYMENT", paymentId, "AI_PLAN_PARTIAL_CANCELLATION_ACCESS_SUSPENDED",
                    "AI 플랜 부분 취소를 반영해 유료 권한을 중지했습니다. 잔여 기간·금액은 운영 대사가 필요합니다.",
                    "cancelledAmount=" + paymentInfo.getCancelledAmount());
            redisLockService.executeWithLock(
                    LockKeys.aiPlanPayment(paymentId), PAYMENT_LOCK_LEASE,
                    () -> paymentCommandExecutor.partiallyCancelPaidSubscriptionInTx(paymentId));
            return;
        }
        if (!"PAID".equalsIgnoreCase(paymentInfo.getStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }
        applyPaidSubscription(paymentId, paymentInfo);
    }

    // 결제 검증 + 구독 반영 — 락을 먼저 잡고, Executor의 @Transactional 메서드가 커밋을 마친 뒤에만 락을 해제한다.
    // Webhook과 completePayment가 동시에 들어와도 같은 paymentId 락으로 직렬화되고, 두 번째 진입은
    // 첫 번째가 커밋한 최신 상태(PAID)를 읽으므로 중복 구독 생성 없이 멱등 스킵/복구 경로로만 흐른다.
    private void applyPaidSubscription(String paymentId) {
        PortOnePaymentInfo paymentInfo;
        try {
            paymentInfo = portOnePaymentClient.getPayment(paymentId);
        } catch (RuntimeException e) {
            operationFailureRecorder.record(
                    OperationFailureCategory.EXTERNAL_API, "AiPlanSubscriptionService.getPayment",
                    "PAYMENT", paymentId, e, null);
            throw e;
        }
        applyPaidSubscription(paymentId, paymentInfo);
    }

    private void applyPaidSubscription(String paymentId, PortOnePaymentInfo paymentInfo) {
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.isFailed() && "PAID".equalsIgnoreCase(paymentInfo.getStatus())) {
            cancelLatePaidPayment(paymentId, payment, paymentInfo);
            return;
        }
        if ("PAID".equalsIgnoreCase(paymentInfo.getStatus())
                && paymentInfo.getAmount() != null
                && payment.getAmount().compareTo(paymentInfo.getAmount()) != 0) {
            cancelMismatchedPayment(paymentId, paymentInfo.getAmount());
        }
        try {
            redisLockService.executeWithLock(
                    LockKeys.aiPlanPayment(paymentId),
                    PAYMENT_LOCK_LEASE,
                    () -> paymentCommandExecutor.applyPaidSubscriptionInTx(paymentId, paymentInfo));
        } catch (BusinessException e) {
            // Executor 트랜잭션이 끝나 잠금이 풀린 뒤 별도 트랜잭션으로 남긴다. 잠긴 결제 행을
            // REQUIRES_NEW에서 다시 갱신하면 MySQL lock wait가 발생할 수 있다.
            if (e.getErrorCode() == ErrorCode.PAYMENT_AMOUNT_MISMATCH) {
                failureRecorder.markFailed(paymentId);
            }
            throw e;
        }
    }

    /**
     * 결제 대기 만료로 FAILED 처리된 뒤 뒤늦게 승인된 결제를 환불한다.
     * 구독은 만들지 않는다 — 고객이 기대한 시점과 기간이 이미 어긋났고, 종료된 결제를
     * 되살리면 만료·구독 상태가 서로 어긋난다.
     */
    private void cancelLatePaidPayment(
            String paymentId, AiPlanPayment payment, PortOnePaymentInfo paymentInfo) {
        java.math.BigDecimal amount = paymentInfo.getAmount() != null
                ? paymentInfo.getAmount() : payment.getAmount();
        operationFailureRecorder.record(
                OperationFailureCategory.REFUND, "AiPlanSubscriptionService.cancelLatePaidPayment",
                "PAYMENT", paymentId, "AI_PLAN_LATE_PAID_AUTO_REFUND",
                "만료된 AI 플랜 결제가 뒤늦게 승인돼 자동 환불을 시작했습니다.", "amount=" + amount);
        cancelMismatchedPayment(paymentId, amount);
    }

    private void cancelMismatchedPayment(String paymentId, java.math.BigDecimal amount) {
        AiPlanPaymentCancellationPlan plan = paymentCommandExecutor
                .prepareMismatchedPaymentCancellation(paymentId, amount);
        executeMismatchedPaymentCancellation(plan);
    }

    /**
     * PENDING 환불 작업을 주기적으로 재개한다. 작업 행이 이미 있으므로 전달한 금액은
     * 새 작업 생성에 쓰이지 않으며, executor가 원래 요청 금액과 멱등키를 반환한다.
     */
    public void retryPendingMismatchedPaymentCancellation(String paymentId) {
        AiPlanPaymentCancellationPlan plan = paymentCommandExecutor
                .prepareMismatchedPaymentCancellation(paymentId, java.math.BigDecimal.ZERO);
        executeMismatchedPaymentCancellation(plan);
    }

    /** Webhook이 유실된 REQUESTED 환불을 PortOne 조회로 확정한다. */
    public void reconcileRequestedMismatchedPaymentCancellation(String paymentId) {
        try {
            PortOnePaymentInfo paymentInfo = portOnePaymentClient.getPayment(paymentId);
            if (!"CANCELLED".equalsIgnoreCase(paymentInfo.getStatus())) {
                log.info("[AI-PLAN] 환불 확정 대기: paymentId={}, status={}", paymentId, paymentInfo.getStatus());
                return;
            }
            paymentCommandExecutor.confirmMismatchedPaymentCancellation(paymentId);
            redisLockService.executeWithLock(
                    LockKeys.aiPlanPayment(paymentId), PAYMENT_LOCK_LEASE,
                    () -> paymentCommandExecutor.cancelPaidSubscriptionInTx(paymentId));
        } catch (RuntimeException e) {
            log.warn("[AI-PLAN] 환불 확정 대사 실패: paymentId={}", paymentId, e);
            operationFailureRecorder.record(
                    OperationFailureCategory.REFUND, "AiPlanSubscriptionService.reconcileRequestedMismatchedPaymentCancellation",
                    "PAYMENT", paymentId, e, null);
        }
    }

    private void executeMismatchedPaymentCancellation(AiPlanPaymentCancellationPlan plan) {
        if (!plan.shouldCallPortOne()) {
            return;
        }
        try {
            PortOneCancelResult result = portOnePaymentClient.cancelPayment(
                    plan.paymentId(), plan.amount(), "AI 플랜 결제 금액 불일치 — 자동 환불", plan.idempotencyKey());
            paymentCommandExecutor.recordMismatchedPaymentCancellationResult(plan.paymentId(), result);
            if (!result.isSucceeded() || result.cancelledAmount() == null
                    || result.cancelledAmount().compareTo(plan.amount()) != 0) {
                operationFailureRecorder.record(
                        OperationFailureCategory.REFUND, "AiPlanSubscriptionService.autoCancel",
                        "PAYMENT", plan.paymentId(), "AI_PLAN_REFUND_" + result.status(),
                        "AI 플랜 금액 불일치 자동 환불이 확정되지 않았습니다.",
                        "cancellationId=" + result.cancellationId() + ", amount=" + result.cancelledAmount());
            }
        } catch (RuntimeException e) {
            log.error("[AI-PLAN] 금액 불일치 자동 환불 실패 — 수동 확인 필요: paymentId={}", plan.paymentId(), e);
            paymentCommandExecutor.recordMismatchedPaymentCancellationFailure(plan.paymentId());
            operationFailureRecorder.record(
                    OperationFailureCategory.REFUND, "AiPlanSubscriptionService.autoCancel",
                    "PAYMENT", plan.paymentId(), e, "결제 금액 불일치 자동 환불 실패, amount=" + plan.amount());
        }
    }

    // 구독 취소 — 정책값에 따라 즉시 FREE 또는 기간 종료까지 유지
    @Transactional
    public void cancelSubscription(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanSubscription subscription = aiPlanSubscriptionRepository
                .findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_INVALID_STATUS, "활성 구독이 없습니다"));

        if (cancelImmediately) {
            subscription.deactivate(LocalDateTime.now());
            log.info("[AI-PLAN] 구독 즉시 취소: storeId={}", store.getStoreId());
        } else {
            // 기간 종료일(expiredAt)까지 유지 — 만료 스케줄러가 비활성화
            log.info("[AI-PLAN] 구독 취소 접수 — 기간 종료일까지 유지: storeId={}, expiredAt={}",
                    store.getStoreId(), subscription.getExpiredAt());
        }
    }
}
