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

    /**
     * 단일 원장을 주차 정산으로 확정한다. 스케줄러가 ID마다 별도 트랜잭션으로 호출한다.
     *
     * @return 이번 주기보다 앞서 지급 가능해졌던 원장(지난 마감에서 누락돼 늦게 포함된 건)이면 {@code true}
     */
    @Transactional
    public boolean closeEligibleRevenue(Long ownerRevenueId, LocalDateTime periodStartAt, LocalDateTime periodEndAt) {
        OwnerRevenue snapshot = ownerRevenueRepository.findById(ownerRevenueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));

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
        if (revenue.getStatus() != OwnerRevenueStatus.ACCRUED
                || revenue.getSettleableAt() == null
                || !revenue.getSettleableAt().isBefore(periodEndAt)) {
            return false;
        }

        revenue.markSettlementPending(periodEndAt);
        weeklySettlementItemRepository.save(WeeklySettlementItem.create(settlement, revenue));

        // 이번 주기 이전에 이미 지급 가능했던 원장이다. 지난 마감이 빠뜨렸다는 뜻이라,
        // 이 정산 행의 기간 컬럼이 포함 원장을 그대로 설명하지 못한다 — 호출자가 기록한다.
        return revenue.getSettleableAt().isBefore(periodStartAt);
    }
}
