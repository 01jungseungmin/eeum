package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 결제만 사장 매출 원장으로 연결한다. AI 플랜 결제는 Order가 없으므로 이 진입점에 들어올 수 없다.
 *
 * <p>원장은 <b>플랫폼이 대신 받아 두었다가 사장에게 보내줄 돈</b>의 기록이다. 그래서
 * 플랫폼을 거치지 않은 결제(현장결제)는 원장을 만들지 않는다 — 자세한 이유는
 * {@link #isPayoutEligible(Payment)} 참고.
 */
@Service
@RequiredArgsConstructor
public class OwnerRevenueService {

    private final OwnerRevenueRepository ownerRevenueRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final WeeklySettlementItemRepository weeklySettlementItemRepository;
    private final SettlementFeePolicy settlementFeePolicy;

    /**
     * 지급 대상 결제인지 판별한다.
     *
     * <p>현장결제는 손님이 매장에서 사장에게 직접 돈을 낸다. 플랫폼은 그 돈을 받은 적이
     * 없으므로 사장에게 보내줄 것도 없다. 원장을 만들면 주간 정산이 그 금액을 지급 대상으로
     * 잡아 <b>사장이 같은 주문 대금을 두 번 받는다.</b>
     */
    private boolean isPayoutEligible(Payment payment) {
        return payment.getPaymentMethod() != PaymentMethod.CASH_ON_SITE;
    }

    /**
     * @return 생성되었거나 이미 존재하는 원장. 지급 대상이 아닌 결제수단이면 {@code null}.
     */
    @Transactional
    public OwnerRevenue recordPaidOrder(Order order, Payment payment) {
        if (order == null || payment == null || payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        if (!isPayoutEligible(payment)) {
            return null;
        }

        return ownerRevenueRepository.findByOrder_OrderId(order.getOrderId())
                .orElseGet(() -> {
                    SettlementFeePolicy.Breakdown breakdown =
                            settlementFeePolicy.breakdown(payment.getAmount());
                    return ownerRevenueRepository.save(OwnerRevenue.create(
                            order,
                            payment,
                            breakdown.paymentAmount(),
                            breakdown.pgFeeAmount(),
                            breakdown.platformFeeAmount(),
                            breakdown.payoutAmount(),
                            breakdown.pgRate(),
                            breakdown.platformRate()
                    ));
                });
    }

    @Transactional
    public void markOrderCompleted(Order order, Payment payment) {
        if (order == null || order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        if (payment == null || !isPayoutEligible(payment)) {
            // 원장이 없는 결제수단이다. 채울 지급 가능 시각도 없다.
            return;
        }
        OwnerRevenue revenue = ownerRevenueRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        revenue.markSettleableAtFromCompletedOrder();
    }

    /**
     * 전액 취소를 원장에 반영한다.
     *
     * <p>분기(항목 있음/없음)를 잠그지 않은 스냅샷으로 정하면, 스냅샷을 읽은 뒤 락을 잡기
     * 전에 마감 스케줄러가 커밋했을 때 엉뚱한 분기로 들어간다. 그래서 스냅샷은 "어떤
     * 정산 행을 잠글지" 고르는 데만 쓰고, 실제 판단은 락을 쥔 뒤 다시 읽은 값으로 한다.
     *
     * <p>잠금 순서는 어느 경로에서나 {@code WeeklySettlement → OwnerRevenue}다.
     * 마감(WeeklySettlementClosingService)과 수동 지급(ManualSettlementPayoutService)도
     * 같은 순서를 쓴다.
     */
    @Transactional
    public void cancelBeforePayout(Long orderId, String reason, LocalDateTime cancelledAt) {
        OwnerRevenue revenueSnapshot = ownerRevenueRepository.findByOrder_OrderId(orderId).orElse(null);
        if (revenueSnapshot == null) {
            // 결제 완료 전 주문이라 원장이 없다. 취소할 대상 자체가 없다.
            return;
        }

        // 항목 유무를 먼저 분기하지 않는다. 항목이 있으면 이 조회가 WeeklySettlement를 먼저
        // 잠그고, 이후 원장을 잠근다. 항목이 없으면 원장 잠금만 잡아 마감과의 경합을 막는다.
        WeeklySettlement lockedSettlement = weeklySettlementRepository
                .findByOwnerRevenueIdWithPessimisticLock(revenueSnapshot.getOwnerRevenueId())
                .orElse(null);
        OwnerRevenue revenue = ownerRevenueRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        // 락을 쥔 뒤 다시 읽는다. 여기서 나오는 값이 판단의 근거다.
        WeeklySettlementItem item = weeklySettlementItemRepository
                .findByOwnerRevenue_OwnerRevenueId(revenue.getOwnerRevenueId())
                .orElse(null);

        if (item == null) {
            if (lockedSettlement != null) {
                // 스냅샷에는 항목이 있었는데 지금은 없다 — 다른 경로가 먼저 취소했다.
                throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
            }
            revenue.cancel(reason, cancelledAt);
            return;
        }

        if (lockedSettlement == null
                || !item.getWeeklySettlement().getWeeklySettlementId()
                        .equals(lockedSettlement.getWeeklySettlementId())) {
            // 잠그지 않은 정산 행의 합계를 건드리게 된다. 잠금 순서를 뒤집는 대신 재시도로 넘긴다.
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }

        revenue.cancelBeforePayout(item, reason, cancelledAt);
        weeklySettlementItemRepository.delete(item);
    }

    /**
     * PG 취소를 호출하기 전에 자동 취소가 가능한 거래인지 확인한다.
     *
     * <p><b>원장 상태만 보면 부족하다.</b> 원장이 {@code SETTLEMENT_PENDING}이어도 그 원장이
     * 속한 주간 정산이 이미 지급 중({@code PAYOUT_IN_PROGRESS})이거나 완료
     * ({@code COMPLETED})일 수 있다. 그대로 PG를 취소하면 고객에게는 환불되고 사장에게는
     * 지급되어 과지급이 된다. 그래서 정산 행의 상태까지 함께 본다.
     *
     * <p>여기서 통과해도 PG 호출과 내부 반영 사이의 경합은 남는다. 그 구간은
     * {@code PaymentCancellationOperation}이 수동 검토로 격리한다.
     */
    @Transactional
    public void assertCancellableBeforePayout(Long orderId) {
        OwnerRevenue snapshot = ownerRevenueRepository.findByOrder_OrderId(orderId).orElse(null);
        if (snapshot == null) {
            return;
        }
        WeeklySettlement settlement = weeklySettlementRepository
                .findByOwnerRevenueIdWithPessimisticLock(snapshot.getOwnerRevenueId())
                .orElse(null);
        OwnerRevenue revenue = ownerRevenueRepository.findByOrderIdWithPessimisticLock(orderId).orElse(null);
        if (revenue == null) {
            return;
        }
        if (revenue.getStatus() != OwnerRevenueStatus.ACCRUED
                && revenue.getStatus() != OwnerRevenueStatus.SETTLEMENT_PENDING) {
            // 이미 지급 완료(SETTLED)거나 이미 취소(CANCELLED)된 원장이다.
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_PAYOUT_STARTED);
        }

        if (settlement != null && settlement.getStatus() != WeeklySettlementStatus.PAYOUT_PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_PAYOUT_STARTED);
        }
    }

    /** 지급 전 부분 취소를 원장과 주간 정산 항목에 원자적으로 반영한다. */
    @Transactional
    public void reconcilePartialCancellation(
            Long orderId, SettlementFeePolicy.Breakdown breakdown
    ) {
        reconcilePartialCancellation(orderId, breakdown, null, null);
    }

    @Transactional
    public void reconcilePartialCancellation(
            Long orderId, SettlementFeePolicy.Breakdown breakdown,
            BigDecimal requestedPgRate, BigDecimal requestedPlatformRate
    ) {
        OwnerRevenue snapshot = ownerRevenueRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        WeeklySettlement settlement = weeklySettlementRepository
                .findByOwnerRevenueIdWithPessimisticLock(snapshot.getOwnerRevenueId()).orElse(null);
        OwnerRevenue revenue = ownerRevenueRepository.findByOrderIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        WeeklySettlementItem item = weeklySettlementItemRepository
                .findByOwnerRevenue_OwnerRevenueId(revenue.getOwnerRevenueId()).orElse(null);
        BigDecimal pgRate = revenue.getPgFeeRate() == null ? requestedPgRate : revenue.getPgFeeRate();
        BigDecimal platformRate = revenue.getPlatformFeeRate() == null
                ? requestedPlatformRate : revenue.getPlatformFeeRate();
        if (pgRate == null || platformRate == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);
        }
        revenue.recordFeeRates(pgRate, platformRate);
        SettlementFeePolicy.Breakdown fixedRateBreakdown = settlementFeePolicy.breakdown(
                breakdown.paymentAmount(), pgRate, platformRate);
        if (settlement != null && settlement.getStatus() != WeeklySettlementStatus.PAYOUT_PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCELLATION_PAYOUT_STARTED);
        }
        if (settlement != null && item == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }
        if (item != null && settlement == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }
        if (item != null) {
            settlement.replaceRevenueAmounts(item, fixedRateBreakdown.paymentAmount(), fixedRateBreakdown.pgFeeAmount(),
                    fixedRateBreakdown.platformFeeAmount(), fixedRateBreakdown.payoutAmount());
        }
        revenue.adjustAmounts(fixedRateBreakdown.paymentAmount(), fixedRateBreakdown.pgFeeAmount(),
                fixedRateBreakdown.platformFeeAmount(), fixedRateBreakdown.payoutAmount());
    }
}
