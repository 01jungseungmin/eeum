package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PaymentCancellationPlan;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.application.settlement.service.SettlementFeePolicy;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.enums.RefundStatus;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 전액 취소의 <b>DB 단계</b>만 담당한다. 외부 호출은 하지 않는다.
 *
 * <p>{@link PaymentCancellationService}가 이 클래스의 메서드를 단계별로 호출한다.
 * 각 메서드가 짧은 독립 트랜잭션이라, 그 사이에 PortOne 호출을 트랜잭션·비관적 락
 * 바깥에서 수행할 수 있다.
 *
 * <p>오케스트레이터와 분리한 이유는 프록시다. 같은 빈 안에서 호출하면
 * {@code @Transactional}이 적용되지 않아 결국 하나의 긴 트랜잭션이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancellationProcessor {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancellationOperationRepository cancellationOperationRepository;
    private final OwnerRevenueRepository ownerRevenueRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final OwnerRevenueService ownerRevenueService;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;
    private final SettlementFeePolicy settlementFeePolicy;

    /**
     * 1단계 — 취소 대상을 확정하고 외부 호출을 준비한다.
     *
     * <p>여기서 커밋된 작업 행이 "PortOne을 호출했을 수 있다"는 사실을 남긴다.
     * 이게 없으면 호출 직후 프로세스가 죽었을 때 흔적이 사라진다.
     *
     * @return 외부 호출에 필요한 정보. {@code null}이면 이미 처리된 취소라 호출할 필요가 없다.
     */
    @Transactional
    public PaymentCancellationPlan prepare(
            Long orderId,
            PaymentCancellationTrigger trigger,
            String reason
    ) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        PaymentCancellationOperation operation = cancellationOperationRepository
                .findByOrderIdWithPessimisticLock(orderId)
                .orElse(null);

        // 이미 끝난 취소는 다시 하지 않는다 — 네 진입점이 같은 주문에 겹쳐 들어와도 한 번만 돈다.
        if (operation != null && operation.isCompleted()) {
            BigDecimal alreadyCancelled = payment.getCancelledAmount() == null
                    ? BigDecimal.ZERO : payment.getCancelledAmount();
            if (alreadyCancelled.compareTo(payment.getAmount()) < 0
                    && (payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                    || trigger != PaymentCancellationTrigger.PORTONE_WEBHOOK)) {
                operation.reopenForRemainingCancellation(
                        payment.getRemainingAmount(), trigger, reason);
            } else {
                log.info("이미 완료된 취소 작업 — 재요청 무시: orderId={}, trigger={}", orderId, trigger);
                return null;
            }
        }
        if (operation != null && operation.isManualReviewRequired()) {
            // 사람이 수습 중인 건을 자동 경로가 다시 건드리면 상태가 더 꼬인다.
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
        }
        if (operation != null && operation.isPgOutcomeUnknown()) {
            return new PaymentCancellationPlan(operation.getPaymentCancellationOperationId(),
                    payment.getPortonePaymentId(), payment.getAmount(), operation.getReason(), false, true,
                    operation.getRequestedAt());
        }

        boolean externalPendingCancellation = trigger == PaymentCancellationTrigger.PORTONE_WEBHOOK
                && payment.getStatus() == PaymentStatus.PENDING;
        if (payment.getStatus() != PaymentStatus.PAID
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED
                && !externalPendingCancellation) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
        }
        if (trigger == PaymentCancellationTrigger.OWNER_REFUND_APPROVAL
                && (payment.getRefundStatus() != RefundStatus.REQUESTED
                || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.EXPIRED)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        if (trigger == PaymentCancellationTrigger.OWNER_ORDER_REJECT
                && order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        if (trigger == PaymentCancellationTrigger.CUSTOMER_CANCEL
                && order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.READY) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        // 정산 지급이 시작된 거래는 PG를 건드리기 전에 막는다. 통과 후의 경합은 4단계가 격리한다.
        ownerRevenueService.assertCancellableBeforePayout(orderId);

        if (operation == null) {
            operation = PaymentCancellationOperation.start(
                    order, payment, trigger, reason, payment.getRemainingAmount());
            operation = cancellationOperationRepository.save(operation);
        }
        operation.markPgRequested(trigger, reason, LocalDateTime.now());

        return new PaymentCancellationPlan(
                operation.getPaymentCancellationOperationId(),
                payment.getPortonePaymentId(),
                payment.getRemainingAmount(),
                reason,
                operation.isPgCancelled(), false, operation.getRequestedAt());
    }

    /**
     * PG 콘솔/웹훅에서 이미 취소된 결제가 지급 시작 뒤 발견된 경우의 격리 경로다.
     * 고객 돈은 이미 움직였을 수 있으므로 자동 취소 차단만 하고 작업 이력을 남기지 않으면 안 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExternallyCancelledAfterPayoutStarted(
            Long orderId, PaymentCancellationTrigger trigger, String reason
    ) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentCancellationOperation operation = cancellationOperationRepository
                .findByOrderIdWithPessimisticLock(orderId)
                .orElseGet(() -> cancellationOperationRepository.save(
                        PaymentCancellationOperation.start(order, payment, trigger, reason, payment.getAmount())));
        operation.requireManualReview("EXTERNAL_CANCEL_AFTER_PAYOUT_STARTED",
                "PG에서 이미 취소됐으나 정산 지급이 시작되어 자동 반영하지 않았습니다.");
    }

    /** 외부 부분 취소 금액을 확인할 수 없을 때 수동 검토로 격리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExternalPartialCancellation(Long orderId) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        OwnerRevenue revenue = ownerRevenueRepository.findByOrder_OrderId(orderId).orElse(null);
        if (revenue != null) {
            // 정산 항목이 있으면 claim과 같은 WeeklySettlement 락을 먼저 잡는다. 항목이 아직
            // 없으면 원장을 잠가 마감 스케줄러가 부분 취소 작업보다 먼저 포함시키지 못하게 한다.
            boolean settlementLocked = weeklySettlementRepository
                    .findByOwnerRevenueIdWithPessimisticLock(revenue.getOwnerRevenueId())
                    .isPresent();
            if (!settlementLocked) {
                ownerRevenueRepository.findByIdWithPessimisticLock(revenue.getOwnerRevenueId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
            }
        }
        PaymentCancellationOperation operation = cancellationOperationRepository
                .findByOrderIdWithPessimisticLock(orderId)
                .orElseGet(() -> cancellationOperationRepository.save(
                        PaymentCancellationOperation.start(
                                order, payment, PaymentCancellationTrigger.PORTONE_WEBHOOK,
                                "PortOne 외부 부분 취소", payment.getAmount())));
        operation.requireManualReview(
                "PARTIAL_CANCEL_RECONCILIATION_REQUIRED",
                "PortOne 부분 취소 금액을 수동 대사하기 전까지 정산 지급을 차단합니다.");
    }

    /** 외부 누적 취소액을 원장과 정산에 반영해 이후 요율 변경의 소급을 막는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileExternalPartialCancellation(Long orderId, BigDecimal cumulativeCancelledAmount) {
        reconcileExternalPartialCancellation(orderId, cumulativeCancelledAmount, null, null);
    }

    public void reconcileExternalPartialCancellation(
            Long orderId, BigDecimal cumulativeCancelledAmount, BigDecimal pgFeeRate, BigDecimal platformFeeRate
    ) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (cumulativeCancelledAmount == null
                || cumulativeCancelledAmount.signum() <= 0
                || cumulativeCancelledAmount.compareTo(payment.getAmount()) >= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
        }
        BigDecimal alreadyCancelled = payment.getCancelledAmount() == null
                ? BigDecimal.ZERO : payment.getCancelledAmount();
        if (cumulativeCancelledAmount.compareTo(alreadyCancelled) <= 0) {
            return;
        }
        SettlementFeePolicy.Breakdown breakdown = settlementFeePolicy.breakdown(
                payment.getAmount().subtract(cumulativeCancelledAmount));
        ownerRevenueService.reconcilePartialCancellation(orderId, breakdown, pgFeeRate, platformFeeRate);
        payment.markPartiallyRefunded(cumulativeCancelledAmount);
        PaymentCancellationOperation operation = cancellationOperationRepository
                .findByOrderIdWithPessimisticLock(orderId)
                .orElseGet(() -> cancellationOperationRepository.save(
                        PaymentCancellationOperation.start(order, payment,
                                PaymentCancellationTrigger.PORTONE_WEBHOOK,
                                "PortOne 외부 부분 취소", payment.getAmount())));
        operation.markPartialReconciled(cumulativeCancelledAmount, LocalDateTime.now());
    }

    /**
     * 2단계 — PG 취소가 확정된 사실만 먼저 커밋한다.
     *
     * <p>3단계(내부 반영)와 나누는 이유는, 내부 반영이 실패해도 "PG는 이미 취소됐다"가
     * 남아야 하기 때문이다. 한 트랜잭션이면 롤백되면서 이 사실까지 사라진다.
     */
    @Transactional
    public void markPgCancelled(Long operationId, PortOneCancelResult result) {
        PaymentCancellationOperation operation = getOperation(operationId);
        operation.markPgCancelled(
                result.cancellationId(), result.status(), result.cancelledAmount(), LocalDateTime.now());
    }

    /** PG 호출 자체가 실패한 경우. 돈이 움직이지 않았으므로 재시도 가능한 상태로 되돌린다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPgFailed(Long operationId, String failureCode, String failureReason) {
        PaymentCancellationOperation operation = getOperation(operationId);
        operation.markPgFailed(failureCode, failureReason);
    }

    /**
     * 3단계 — 결제·주문·재고·정산 원장에 취소를 반영한다.
     *
     * <p>결제 상태 전이는 경로마다 다르다. 사장이 환불 요청을 승인한 건은
     * {@code REFUNDED}로, 나머지는 {@code CANCELLED}로 간다. 고객 화면과 정산 대사에서
     * "환불"과 "취소"를 구분해야 하므로 한쪽으로 뭉뚱그리지 않는다.
     */
    @Transactional
    public void applyCancellation(Long operationId, PaymentCancellationTrigger trigger, String reason) {
        Long orderId = cancellationOperationRepository.findOrderIdByOperationId(operationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS));

        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        // prepare와 같은 Order → Payment → Operation 순서로 잠근다.
        PaymentCancellationOperation operation = getOperation(operationId);

        // PG 취소가 확정되기 전에는 내부 결제·주문·정산을 절대 바꾸지 않는다.
        // 이 메서드는 public이라 오케스트레이터 밖의 호출도 이 경계를 우회할 수 없다.
        if (!operation.isPgCancelled()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);
        }

        if (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            if (trigger == PaymentCancellationTrigger.OWNER_REFUND_APPROVAL) {
                payment.completeRefund();
            } else {
                payment.cancel();
            }
        } else if (payment.getStatus() == PaymentStatus.PENDING) {
            // 외부 콘솔에서 결제 대기 건을 취소한 Webhook도 내부 주문과 맞춘다.
            payment.cancel();
        }

        // 재고 복원과 주문 종료는 이미 끝난 주문에서 건너뛴다 — 재시도해도 재고가 두 번 늘지 않는다.
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.EXPIRED) {
            orderService.restoreStockForOrder(orderId);
            order.cancel(reason);
        }

        ownerRevenueService.cancelBeforePayout(orderId, reason, LocalDateTime.now());
        operation.markCompleted(LocalDateTime.now());
        String changedStatus = trigger == PaymentCancellationTrigger.OWNER_REFUND_APPROVAL ? "환불완료"
                : trigger == PaymentCancellationTrigger.OWNER_ORDER_REJECT ? "거절" : "취소완료";
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getAccount().getAccountId(),
                order.getStore().getName(),
                order.getOrderNumber(), changedStatus, orderId));
    }

    /**
     * PG 취소 확정 뒤 내부 반영이 실패한 수동 검토 건을 다시 반영한다.
     * 외부 API를 호출하지 않으며, 성공 여부가 불명확하거나 부분 취소인 건은 여기로
     * 들어올 수 없다.
     */
    @Transactional
    public void applyConfirmedManualReviewCancellation(Long orderId) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        paymentRepository.findByOrderIdWithPessimisticLock(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentCancellationOperation operation = cancellationOperationRepository
                .findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS));

        operation.resumeConfirmedPgCancellation();
        applyCancellation(operation.getPaymentCancellationOperationId(),
                operation.getTriggerType(), operation.getReason());
    }

    /**
     * 4단계(실패 경로) — 사람이 확인해야 할 건으로 격리한다.
     *
     * <p>{@code REQUIRES_NEW}인 이유는, 3단계 트랜잭션이 롤백되는 와중에 호출되기 때문이다.
     * 같은 트랜잭션에 얹으면 이 기록까지 함께 사라져 격리가 성립하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireManualReview(Long operationId, String failureCode, String failureReason) {
        PaymentCancellationOperation operation = getOperation(operationId);
        operation.requireManualReview(failureCode, failureReason);
        log.error("취소 수동 검토 필요 — operationId={}, orderId={}, code={}, reason={}",
                operationId, operation.getOrder().getOrderId(), failureCode, failureReason);
    }

    /**
     * 응답 유실 뒤 유예 시간까지 지나도록 PG 결과가 확정되지 않은 작업을 운영 대기열로 보낸다.
     *
     * <p>현재 상태를 비관적으로 다시 잠그고 확인하므로, 선행 요청이 그 사이 성공을 반영한
     * 경우에는 아무 변경도 하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean requireManualReviewForStalePgRequest(Long operationId, Duration gracePeriod) {
        PaymentCancellationOperation operation = getOperation(operationId);
        boolean marked = operation.requireManualReviewForUnknownPg(
                gracePeriod,
                LocalDateTime.now(),
                "PG_CANCEL_OUTCOME_UNKNOWN",
                "PortOne 취소 요청 후 최종 응답을 확인하지 못했습니다.");
        if (marked) {
            log.error("PG 취소 결과 유실 — 수동 검토 필요: operationId={}, orderId={}",
                    operationId, operation.getOrder().getOrderId());
        }
        return marked;
    }

    /** PG 결과 또는 취소 금액이 확정되지 않은 경우. 상태·원인을 남기고 자동 반영을 막는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPendingPgStatus(
            Long operationId, PortOneCancelResult result, String failureCode, String failureReason
    ) {
        PaymentCancellationOperation operation = getOperation(operationId);
        operation.recordPgStatus(result.cancellationId(), result.status(), result.cancelledAmount());
        operation.requireManualReview(failureCode, failureReason);
    }

    private PaymentCancellationOperation getOperation(Long operationId) {
        return cancellationOperationRepository.findByIdWithPessimisticLock(operationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS));
    }
}
