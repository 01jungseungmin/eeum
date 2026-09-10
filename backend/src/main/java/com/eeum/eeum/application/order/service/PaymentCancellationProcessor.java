package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PaymentCancellationPlan;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final OwnerRevenueService ownerRevenueService;
    private final OrderService orderService;

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
            log.info("이미 완료된 취소 작업 — 재요청 무시: orderId={}, trigger={}", orderId, trigger);
            return null;
        }
        if (operation != null && operation.isManualReviewRequired()) {
            // 사람이 수습 중인 건을 자동 경로가 다시 건드리면 상태가 더 꼬인다.
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
        }

        boolean externalPendingCancellation = trigger == PaymentCancellationTrigger.PORTONE_WEBHOOK
                && payment.getStatus() == PaymentStatus.PENDING;
        if (payment.getStatus() != PaymentStatus.PAID && !externalPendingCancellation) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
        }

        // 정산 지급이 시작된 거래는 PG를 건드리기 전에 막는다. 통과 후의 경합은 4단계가 격리한다.
        ownerRevenueService.assertCancellableBeforePayout(orderId);

        if (operation == null) {
            operation = PaymentCancellationOperation.start(
                    order, payment, trigger, reason, payment.getAmount());
            operation = cancellationOperationRepository.save(operation);
        }
        operation.markPgRequested(trigger, reason, LocalDateTime.now());

        return new PaymentCancellationPlan(
                operation.getPaymentCancellationOperationId(),
                payment.getPortonePaymentId(),
                payment.getAmount(),
                reason,
                operation.isPgCancelled());
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
        operation.markPgCancelled(result.cancellationId(), result.status(), LocalDateTime.now());
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
        PaymentCancellationOperation operation = getOperation(operationId);
        Long orderId = operation.getOrder().getOrderId();

        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PAID) {
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

    /** PortOne이 REQUESTED만 돌려준 경우. 완료로 확정하지 않고 상태만 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPendingPgStatus(Long operationId, PortOneCancelResult result) {
        PaymentCancellationOperation operation = getOperation(operationId);
        operation.recordPgStatus(result.cancellationId(), result.status());
        operation.requireManualReview(
                "PG_CANCEL_NOT_CONFIRMED",
                "PortOne 취소 상태가 SUCCEEDED가 아님: " + result.status());
    }

    private PaymentCancellationOperation getOperation(Long operationId) {
        return cancellationOperationRepository.findById(operationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS));
    }
}
