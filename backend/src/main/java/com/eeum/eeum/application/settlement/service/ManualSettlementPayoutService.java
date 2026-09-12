package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualSettlementPayoutService {

    private final WeeklySettlementRepository weeklySettlementRepository;
    private final AccountRepository accountRepository;
    private final WeeklySettlementItemRepository weeklySettlementItemRepository;
    private final OwnerRevenueRepository ownerRevenueRepository;
    private final PaymentCancellationOperationRepository cancellationOperationRepository;

    @Transactional
    public void claim(Long adminAccountId, Long weeklySettlementId, String claimToken, LocalDateTime expiresAt) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        List<Long> includedOrderIds = weeklySettlementItemRepository
                .findOrderIdsByWeeklySettlementId(weeklySettlementId);
        if (includedOrderIds.isEmpty()) {
            // 지급할 항목이 없다. 상태가 잘못된 것과 원인이 달라 코드를 나눈다.
            throw new BusinessException(ErrorCode.SETTLEMENT_NO_PAYOUT_TARGET);
        }
        if (cancellationOperationRepository.existsUncompletedByOrderIds(
                includedOrderIds, PaymentCancellationStatus.COMPLETED)) {
            // 취소가 진행 중인 주문이 섞여 있다. 지급을 시작하면 과지급이 된다.
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        settlement.claim(admin, claimToken, expiresAt, LocalDateTime.now(), LocalDateTime.now());
    }

    @Transactional
    public void complete(Long adminAccountId, Long weeklySettlementId, String claimToken, String payoutReference) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        // claim은 먼저 검증하되 COMPLETED 전이는 포함 원장이 모두 SETTLED가 된 뒤에 한다.
        settlement.validateManualCompletion(admin, claimToken, payoutReference, LocalDateTime.now());
        for (Long ownerRevenueId : weeklySettlementItemRepository
                .findOwnerRevenueIdsByWeeklySettlementId(weeklySettlementId)) {
            OwnerRevenue revenue = ownerRevenueRepository.findByIdWithPessimisticLock(ownerRevenueId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
            revenue.markSettled();
        }
        settlement.completeManually(admin, claimToken, payoutReference, LocalDateTime.now());
    }
}
