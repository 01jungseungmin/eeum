package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.dto.response.BlockingCancellationResponseDto;
import com.eeum.eeum.application.order.service.PaymentCancellationService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemAmounts;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualSettlementPayoutService {

    /**
     * 지급 작업 임대 시간. 은행 이체와 확인에 걸리는 시간을 감안한다.
     * 만료돼도 자동으로 풀리지 않는다 — 송금 여부를 확인한 뒤 인계로만 정리한다.
     */
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(30);

    private final WeeklySettlementRepository weeklySettlementRepository;
    private final AccountRepository accountRepository;
    private final WeeklySettlementItemRepository weeklySettlementItemRepository;
    private final OwnerRevenueRepository ownerRevenueRepository;
    private final PaymentCancellationOperationRepository cancellationOperationRepository;
    private final PaymentCancellationService paymentCancellationService;
    private final WeeklySettlementClosingService weeklySettlementClosingService;

    /**
     * 지급 작업을 선점한다.
     *
     * @return 이후 완료 처리에 되돌려줘야 하는 claim 토큰
     */
    @Transactional
    public String claim(Long adminAccountId, Long weeklySettlementId) {
        Account admin = requireAdmin(adminAccountId);
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        assertNoUncompletedCancellation(weeklySettlementId, true);

        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        settlement.claim(admin, claimToken, now.plus(CLAIM_LEASE), now, now);
        return claimToken;
    }

    @Transactional
    public void complete(Long adminAccountId, Long weeklySettlementId, String claimToken, String payoutReference) {
        Account admin = requireAdmin(adminAccountId);
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        // claim은 먼저 검증하되 COMPLETED 전이는 포함 원장이 모두 SETTLED가 된 뒤에 한다.
        settlement.validateManualCompletion(admin, claimToken, payoutReference, LocalDateTime.now());
        assertNoUncompletedCancellation(weeklySettlementId, false);

        // 증분으로 유지해 온 합계를 항목 원본과 맞춰 본다. 어긋나면 지급하지 않는다.
        WeeklySettlementItemAmounts itemAmounts = weeklySettlementItemRepository
                .sumAmountsByWeeklySettlementId(weeklySettlementId);
        settlement.reconcileWithItemAmounts(
                itemAmounts.paymentAmount(), itemAmounts.pgFeeAmount(),
                itemAmounts.platformFeeAmount(), itemAmounts.payoutAmount());

        List<Long> ownerRevenueIds = weeklySettlementItemRepository
                .findOwnerRevenueIdsByWeeklySettlementId(weeklySettlementId);
        List<OwnerRevenue> revenues = ownerRevenueRepository.findAllByIdInWithPessimisticLock(ownerRevenueIds);
        if (revenues.size() != ownerRevenueIds.size()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }
        for (OwnerRevenue revenue : revenues) {
            revenue.markSettled();
        }
        settlement.completeManually(admin, claimToken, payoutReference, LocalDateTime.now());
    }

    /**
     * 임대가 끝난 지급 작업을 인계받아 완료 처리한다. 이전 관리자가 실제로 송금한 것을
     * 확인했을 때만 쓴다. 송금 증빙(payoutReference)을 남겨야 이중 송금 여부를 나중에 가릴 수 있다.
     */
    @Transactional
    public void completeHandover(Long adminAccountId, Long weeklySettlementId, String payoutReference) {
        Account admin = requireAdmin(adminAccountId);
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        LocalDateTime now = LocalDateTime.now();
        if (!settlement.isStalledClaim(now)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        assertNoUncompletedCancellation(weeklySettlementId, false);

        WeeklySettlementItemAmounts itemAmounts = weeklySettlementItemRepository
                .sumAmountsByWeeklySettlementId(weeklySettlementId);
        settlement.reconcileWithItemAmounts(
                itemAmounts.paymentAmount(), itemAmounts.pgFeeAmount(),
                itemAmounts.platformFeeAmount(), itemAmounts.payoutAmount());

        List<Long> ownerRevenueIds = weeklySettlementItemRepository
                .findOwnerRevenueIdsByWeeklySettlementId(weeklySettlementId);
        List<OwnerRevenue> revenues = ownerRevenueRepository.findAllByIdInWithPessimisticLock(ownerRevenueIds);
        if (revenues.size() != ownerRevenueIds.size()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }
        revenues.forEach(OwnerRevenue::markSettled);
        settlement.completeHandover(admin, payoutReference, now);
        log.warn("[SETTLEMENT] 지급 인계 완료 처리: settlementId={}, adminId={}, payoutReference={}",
                weeklySettlementId, adminAccountId, payoutReference);
    }

    /**
     * 임대가 끝난 지급 작업을 되돌려 다시 지급할 수 있게 한다.
     * 이전 관리자가 송금하지 않은 것을 확인했을 때만 쓴다.
     */
    @Transactional
    public void releaseStalledClaim(Long adminAccountId, Long weeklySettlementId, String reason) {
        Account admin = requireAdmin(adminAccountId);
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        settlement.releaseStalledClaim(admin, reason, LocalDateTime.now());
        log.warn("[SETTLEMENT] 미송금 확인 후 지급 작업 반환: settlementId={}, adminId={}, reason={}",
                weeklySettlementId, adminAccountId, reason);
    }

    /**
     * 이 정산의 지급을 막고 있는 취소 작업 목록.
     *
     * 지급 차단은 의도된 격리지만, 무엇이 막고 있는지 볼 수 없으면 관리자가 해소할 수
     * 없다. 차단 판단과 같은 조회를 그대로 노출한다.
     */
    @Transactional(readOnly = true)
    public List<BlockingCancellationResponseDto> getBlockingCancellations(
            Long adminAccountId, Long weeklySettlementId
    ) {
        requireAdmin(adminAccountId);
        List<Long> includedOrderIds = weeklySettlementItemRepository
                .findOrderIdsByWeeklySettlementId(weeklySettlementId);
        if (includedOrderIds.isEmpty()) {
            return List.of();
        }
        return findUncompletedCancellations(includedOrderIds).stream()
                .map(BlockingCancellationResponseDto::from)
                .toList();
    }

    /**
     * PG 전액 취소가 확정됐지만 내부 반영 실패로 지급을 막는 작업을 재시도한다.
     * 성공 여부나 금액이 불명확한 작업은 자동으로 차단을 풀지 않는다.
     */
    public void applyConfirmedCancellation(Long adminAccountId, Long weeklySettlementId, Long orderId) {
        requireAdmin(adminAccountId);
        boolean included = weeklySettlementItemRepository
                .findOrderIdsByWeeklySettlementId(weeklySettlementId)
                .contains(orderId);
        if (!included) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        paymentCancellationService.applyConfirmedManualReviewCancellation(orderId);
    }

    /** 관리자가 확인한 누적 부분 취소액을 지급 전 원장과 정산 항목에 반영한다. */
    public void reconcilePartialCancellation(
            Long adminAccountId, Long weeklySettlementId, Long orderId, BigDecimal cumulativeCancelledAmount,
            BigDecimal pgFeeRate, BigDecimal platformFeeRate
    ) {
        requireAdmin(adminAccountId);
        boolean included = weeklySettlementItemRepository
                .findOrderIdsByWeeklySettlementId(weeklySettlementId)
                .contains(orderId);
        if (!included) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        paymentCancellationService.reconcileExternalPartialCancellation(
                orderId, cumulativeCancelledAmount, pgFeeRate, platformFeeRate);
    }

    /**
     * 누락 원장을 지급 가능해진 원래 주차로 다시 마감한다.
     * 이미 완료된 주차의 합계를 덮어쓰는 것은 금지하며, 그런 건은 별도 지급 보정 절차로
     * 수습해야 한다.
     */
    @Transactional
    public void recoverLateRevenue(Long adminAccountId, Long ownerRevenueId) {
        requireAdmin(adminAccountId);
        OwnerRevenue revenue = ownerRevenueRepository.findById(ownerRevenueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        LocalDateTime currentPeriodStartAt = LocalDateTime.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        if (revenue.getStatus() != com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus.ACCRUED
                || revenue.getSettleableAt() == null
                || !revenue.getSettleableAt().isBefore(currentPeriodStartAt)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }

        LocalDateTime periodStartAt = revenue.getSettleableAt()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        boolean included = weeklySettlementClosingService.closeEligibleRevenue(
                ownerRevenueId, periodStartAt, periodStartAt.plusWeeks(1));
        if (!included) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }
    }

    private Account requireAdmin(Long adminAccountId) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }
        return admin;
    }

    private void assertNoUncompletedCancellation(Long weeklySettlementId, boolean requirePayoutTarget) {
        List<Long> includedOrderIds = weeklySettlementItemRepository
                .findOrderIdsByWeeklySettlementId(weeklySettlementId);
        if (includedOrderIds.isEmpty()) {
            if (requirePayoutTarget) {
                throw new BusinessException(ErrorCode.SETTLEMENT_NO_PAYOUT_TARGET);
            }
            throw new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
        }

        List<PaymentCancellationOperation> blocking = findUncompletedCancellations(includedOrderIds);
        if (!blocking.isEmpty()) {
            // 어느 주문이 막고 있는지 남기지 않으면 관리자가 손쓸 방법이 없다.
            log.warn("정산 지급 차단 — weeklySettlementId={}, 미완료 취소 작업={}",
                    weeklySettlementId,
                    blocking.stream()
                            .map(o -> o.getOrder().getOrderId() + ":" + o.getStatus())
                            .toList());
            throw new BusinessException(ErrorCode.SETTLEMENT_BLOCKED_BY_CANCELLATION);
        }
    }

    private List<PaymentCancellationOperation> findUncompletedCancellations(List<Long> orderIds) {
        return cancellationOperationRepository.findUncompletedByOrderIds(
                orderIds, PaymentCancellationStatus.COMPLETED);
    }
}
