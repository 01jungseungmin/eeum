package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WeeklySettlementClosingService {

    private final OwnerRevenueRepository ownerRevenueRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final WeeklySettlementItemRepository weeklySettlementItemRepository;

    /** 단일 원장을 해당 주차 정산으로 확정한다. 스케줄러가 ID마다 별도 트랜잭션으로 호출한다. */
    @Transactional
    public boolean closeEligibleRevenue(Long ownerRevenueId, LocalDateTime periodStartAt, LocalDateTime periodEndAt) {
        OwnerRevenue snapshot = ownerRevenueRepository.findById(ownerRevenueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));

        // 정산 기간은 포함 원장을 설명하는 계약이다. 지난 마감에서 누락된 원장을 현재
        // 주차에 섞으면 지급액은 맞아도 기간별 정산서가 거짓이 된다.
        if (!isEligibleForPeriod(snapshot, periodStartAt, periodEndAt)) {
            return false;
        }

        Long storeId = snapshot.getStore().getStoreId();
        String idempotencyKey = "weekly:" + storeId + ":" + periodEndAt;
        WeeklySettlement.validateCreation(snapshot.getStore(), periodStartAt, periodEndAt, idempotencyKey);

        weeklySettlementRepository.insertIfAbsent(storeId, periodStartAt, periodEndAt, idempotencyKey);
        WeeklySettlement settlement = weeklySettlementRepository.findByStoreAndPeriodWithPessimisticLock(
                        storeId, periodStartAt, periodEndAt)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));

        // 정산 행을 먼저 잠근 뒤 원장을 current read 한다. 취소 경로도 이 순서를 공유한다.
        OwnerRevenue revenue = ownerRevenueRepository.findByIdWithPessimisticLock(ownerRevenueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        if (!isEligibleForPeriod(revenue, periodStartAt, periodEndAt)) {
            return false;
        }

        revenue.markSettlementPending(periodEndAt);
        weeklySettlementItemRepository.save(WeeklySettlementItem.create(settlement, revenue));

        return false;
    }

    private boolean isEligibleForPeriod(
            OwnerRevenue revenue, LocalDateTime periodStartAt, LocalDateTime periodEndAt
    ) {
        return revenue.getStatus() == OwnerRevenueStatus.ACCRUED
                && revenue.getSettleableAt() != null
                && !revenue.getSettleableAt().isBefore(periodStartAt)
                && revenue.getSettleableAt().isBefore(periodEndAt);
    }
}
