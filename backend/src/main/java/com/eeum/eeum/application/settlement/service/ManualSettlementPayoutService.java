package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.dto.response.BlockingCancellationResponseDto;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualSettlementPayoutService {

    /** 지급 작업 임대 시간. 만료되면 다른 관리자가 다시 claim 할 수 있다. */
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(10);

    private final WeeklySettlementRepository weeklySettlementRepository;
    private final AccountRepository accountRepository;
    private final WeeklySettlementItemRepository weeklySettlementItemRepository;
    private final OwnerRevenueRepository ownerRevenueRepository;
    private final PaymentCancellationOperationRepository cancellationOperationRepository;

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
     * 이 정산의 지급을 막고 있는 취소 작업 목록.
     *
     * <p>지급 차단은 의도된 격리지만, 무엇이 막고 있는지 볼 수 없으면 관리자가 해소할 수
     * 없다. 차단 판단과 <b>같은 조회</b>를 그대로 노출한다.
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
