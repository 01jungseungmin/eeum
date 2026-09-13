package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.order.dto.response.PaymentCancellationPlan;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 전액 취소의 <b>단일 진입점</b>.
 *
 * <p>고객 결제 취소, 사장 환불 승인, 사장 주문 거절, PortOne 취소 Webhook —
 * 네 경로가 모두 이 서비스를 호출한다. 취소 정책이 한 곳에만 있어야
 * "어떤 경로로 들어왔느냐"에 따라 금전 처리가 달라지는 일이 없다.
 *
 * <p><b>이 클래스에는 {@code @Transactional}이 없다.</b> 의도적이다. PortOne 호출을
 * 트랜잭션과 비관적 락 바깥에서 해야 외부 지연이 DB 커넥션과 주문 행을 붙잡지 않는다.
 * DB 작업은 {@link PaymentCancellationProcessor}의 짧은 트랜잭션들이 나눠 맡는다.
 *
 * <pre>
 *   Redis 주문 락
 *     ├─ TX1 prepare        : 대상 확정 + 작업 행 커밋
 *     ├─ (트랜잭션 밖) PortOne 취소
 *     ├─ TX2 markPgCancelled: PG 취소 확정 사실만 커밋
 *     └─ TX3 apply          : 결제·주문·재고·정산 반영
 *          실패 시 → TX(REQUIRES_NEW) requireManualReview
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancellationService {

    private static final Duration CANCEL_LOCK_LEASE_TIME = Duration.ofSeconds(30);

    /**
     * PG 요청 뒤 응답이 유실됐다고 보기 전까지 기다리는 시간.
     * 이 시간 안의 재시도는 최초 호출이 결과를 반영할 수 있게 상태를 그대로 보존한다.
     */
    private static final Duration PG_OUTCOME_RECOVERY_GRACE_PERIOD = Duration.ofMinutes(2);

    /**
     * 내부 반영 재시도 횟수.
     *
     * <p>마감 스케줄러와 부딪혀 나는 충돌은 다시 읽으면 풀린다. 그런 일시적 경합까지
     * 수동 검토로 넘기면 운영자가 볼 게 너무 많아져 진짜 사고가 묻힌다.
     */
    private static final int APPLY_MAX_ATTEMPTS = 3;

    private final RedisLockService redisLockService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PaymentCancellationProcessor processor;
    private final OperationFailureRecorder operationFailureRecorder;

    /**
     * 주문의 결제를 전액 취소한다.
     *
     * <p>같은 주문에 대한 모든 취소·환불·상태 변경과 같은 락 키를 쓴다.
     *
     * @param pgAlreadyCancelled 외부에서 이미 취소된 결제인지. PortOne 취소 Webhook으로
     *                           들어온 건은 PG가 이미 취소된 상태라 다시 호출하면 안 된다.
     */
    public void cancel(
            Long orderId,
            PaymentCancellationTrigger trigger,
            String reason,
            boolean pgAlreadyCancelled
    ) {
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                CANCEL_LOCK_LEASE_TIME,
                ErrorCode.LOCK_ORDER_FAILED,
                () -> {
                    cancelWithLock(orderId, trigger, reason, pgAlreadyCancelled);
                    return null;
                }
        );
    }

    public void cancel(Long orderId, PaymentCancellationTrigger trigger, String reason) {
        cancel(orderId, trigger, reason, false);
    }

    /**
     * 외부 부분 취소는 현재 전액 취소 작업으로 금액을 안전하게 역산할 수 없다.
     * 지급 claim이 이 주문을 포함하지 못하도록 작업을 수동 검토로 격리한다.
     */
    public void recordExternalPartialCancellation(Long orderId) {
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                CANCEL_LOCK_LEASE_TIME,
                ErrorCode.LOCK_ORDER_FAILED,
                () -> {
                    processor.recordExternalPartialCancellation(orderId);
                    return null;
                }
        );
    }

    /**
     * 관리자가 확인한 PG 전액 취소의 내부 반영만 재시도한다.
     * PortOne 호출은 하지 않고, 기존 취소·환불 경로와 같은 주문 락을 쓴다.
     */
    public void applyConfirmedManualReviewCancellation(Long orderId) {
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                CANCEL_LOCK_LEASE_TIME,
                ErrorCode.LOCK_ORDER_FAILED,
                () -> {
                    processor.applyConfirmedManualReviewCancellation(orderId);
                    return null;
                }
        );
    }

    private void cancelWithLock(
            Long orderId,
            PaymentCancellationTrigger trigger,
            String reason,
            boolean pgAlreadyCancelled
    ) {
        PaymentCancellationPlan plan;
        try {
            plan = processor.prepare(orderId, trigger, reason);
        } catch (BusinessException e) {
            if (pgAlreadyCancelled && e.getErrorCode() == ErrorCode.PAYMENT_CANCELLATION_PAYOUT_STARTED) {
                processor.recordExternallyCancelledAfterPayoutStarted(orderId, trigger, reason);
                operationFailureRecorder.record(
                        OperationFailureCategory.REFUND,
                        "PaymentCancellationService.cancel",
                        "order", String.valueOf(orderId),
                        "EXTERNAL_CANCEL_AFTER_PAYOUT_STARTED",
                        "PG 외부 취소가 지급 시작 후 감지되어 수동 검토로 격리했습니다.",
                        "trigger=" + trigger);
                throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
            }
            throw e;
        }
        if (plan == null) {
            // 이미 완료된 취소 — 멱등하게 무시한다.
            return;
        }
        boolean pgCancellationConfirmed = plan.alreadyPgCancelled();
        if (plan.pgOutcomeUnknown() && pgAlreadyCancelled) {
            // 최초 PG 요청의 응답은 유실됐어도 PortOne CANCELLED Webhook은 확정 신호다.
            // 이 신호를 수동 검토로 버리면 고객 환불 뒤 내부 원장만 PAID로 남는다.
            processor.markPgCancelled(plan.operationId(),
                    new PortOneCancelResult(PortOneCancelResult.SUCCEEDED, null, plan.amount()));
            pgCancellationConfirmed = true;
        } else if (plan.pgOutcomeUnknown()) {
            // 이미 PG를 호출했을 수 있는 작업이다. 유예 중에는 선행 호출이 성공 응답을
            // 반영할 수 있게 보존하되, 응답 유실 상태가 오래 지속되면 반드시 수습 대기열에 남긴다.
            boolean markedForManualReview = processor.requireManualReviewForStalePgRequest(
                    plan.operationId(), PG_OUTCOME_RECOVERY_GRACE_PERIOD);
            if (markedForManualReview) {
                operationFailureRecorder.record(
                        OperationFailureCategory.REFUND,
                        "PaymentCancellationService.cancel",
                        "order", String.valueOf(orderId),
                        "PG_CANCEL_OUTCOME_UNKNOWN",
                        "PortOne 취소 요청 결과가 유예 시간 내 확정되지 않아 수동 검토로 격리했습니다.",
                        "trigger=" + trigger + ", operationId=" + plan.operationId());
                throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
            }
            // 아직 유예 구간이다 — 격리된 것이 없으므로 "관리자 확인"이라고 말하지 않는다.
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_IN_PROGRESS);
        }

        if (!pgCancellationConfirmed && !pgAlreadyCancelled) {
            PortOneCancelResult result = callPortOne(plan, orderId);

            // SUCCEEDED만 취소 완료로 확정한다. REQUESTED는 아직 돈이 돌아갔다고 말할 수 없다.
            if (!result.isSucceeded()) {
                isolateUnconfirmedCancellation(plan, orderId, trigger, result,
                        "PG_CANCEL_NOT_CONFIRMED",
                        "PortOne 취소 상태가 SUCCEEDED가 아님: " + result.status());
            }

            // 전액 취소를 요청했는데 부분만 취소됐다면 원장·정산 금액과 어긋난다.
            BigDecimal cancelledAmount = result.cancelledAmount();
            if (cancelledAmount != null && cancelledAmount.compareTo(plan.amount()) != 0) {
                isolateUnconfirmedCancellation(plan, orderId, trigger, result,
                        "PG_CANCEL_AMOUNT_MISMATCH",
                        "요청 금액 " + plan.amount() + " / 취소 금액 " + cancelledAmount);
            }

            if (cancelledAmount == null) {
                // 상태만 SUCCEEDED여도 취소 금액을 읽지 못하면 부분 취소인지 증명할 수 없다.
                // 전액 주문·원장을 취소하면 고객 환불액보다 사장 지급액을 더 차감할 수 있어
                // 재조회 또는 운영 대사가 끝날 때까지 자동 반영하지 않는다.
                isolateUnconfirmedCancellation(plan, orderId, trigger, result,
                        "PG_CANCEL_AMOUNT_UNVERIFIED",
                        "PortOne 취소 응답에서 취소 금액을 읽지 못했습니다.");
            }
            processor.markPgCancelled(plan.operationId(), result);
        } else if (!pgCancellationConfirmed) {
            // 외부에서 이미 취소된 건. PG 호출 없이 확정 사실만 기록한다.
            processor.markPgCancelled(plan.operationId(),
                    new PortOneCancelResult(PortOneCancelResult.SUCCEEDED, null, plan.amount()));
        }

        applyWithRetry(plan, trigger, reason, orderId);
    }

    /** 취소가 확정되지 않은 건을 운영 대기열로 보내고 요청을 중단한다. */
    private void isolateUnconfirmedCancellation(
            PaymentCancellationPlan plan,
            Long orderId,
            PaymentCancellationTrigger trigger,
            PortOneCancelResult result,
            String failureCode,
            String failureReason
    ) {
        processor.recordPendingPgStatus(plan.operationId(), result, failureCode, failureReason);
        operationFailureRecorder.record(
                OperationFailureCategory.REFUND,
                "PaymentCancellationService.cancel",
                "order", String.valueOf(orderId),
                failureCode, failureReason,
                "trigger=" + trigger + ", cancellationId=" + result.cancellationId());
        throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
    }

    private PortOneCancelResult callPortOne(PaymentCancellationPlan plan, Long orderId) {
        try {
            return portOnePaymentClient.cancelPayment(plan.portonePaymentId(), plan.amount(), plan.reason(),
                    "payment-cancel-" + plan.operationId());
        } catch (RuntimeException e) {
            // PG 호출이 실패했으면 돈은 그대로다. 재시도 가능한 상태로 되돌리고 이력만 남긴다.
            processor.markPgFailed(plan.operationId(), resolveCode(e), e.getMessage());
            operationFailureRecorder.record(
                    OperationFailureCategory.REFUND,
                    "PaymentCancellationService.cancel",
                    "order", String.valueOf(orderId),
                    e,
                    "portonePaymentId=" + plan.portonePaymentId() + ", amount=" + plan.amount());
            throw e;
        }
    }

    /**
     * PG 취소가 확정된 뒤의 내부 반영.
     *
     * <p>여기서부터는 <b>실패해도 롤백으로 끝내면 안 된다.</b> 고객 돈은 이미 돌아갔는데
     * 내부에 취소 흔적이 없으면, 사장에게 그대로 지급되고 아무도 그 사실을 모른다.
     * 그래서 재시도로 수렴시키고, 그래도 안 되면 수동 검토로 격리한다.
     */
    private void applyWithRetry(
            PaymentCancellationPlan plan,
            PaymentCancellationTrigger trigger,
            String reason,
            Long orderId
    ) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= APPLY_MAX_ATTEMPTS; attempt++) {
            try {
                processor.applyCancellation(plan.operationId(), trigger, reason);
                return;
            } catch (BusinessException e) {
                lastError = e;
                if (e.getErrorCode() != ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION) {
                    break;
                }
                log.warn("취소 반영 경합 — 재시도 {}/{}: orderId={}", attempt, APPLY_MAX_ATTEMPTS, orderId);
            } catch (RuntimeException e) {
                lastError = e;
                break;
            }
        }

        processor.requireManualReview(plan.operationId(), resolveCode(lastError),
                lastError != null ? lastError.getMessage() : null);
        operationFailureRecorder.record(
                OperationFailureCategory.REFUND,
                "PaymentCancellationService.applyCancellation",
                "order", String.valueOf(orderId),
                lastError,
                "PG 취소는 완료됐으나 내부 반영 실패 — trigger=" + trigger);
        throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
    }

    private String resolveCode(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return error != null ? error.getClass().getSimpleName() : "UNKNOWN";
    }
}
