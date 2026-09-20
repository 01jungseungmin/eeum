package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
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
    private final OperationFailureLogRepository operationFailureLogRepository;

    private static final String LATE_REVENUE_OPERATION = "WeeklySettlementClosingScheduler.closeWeeklySettlements";
    private static final String LATE_REVENUE_ERROR_CODE = "SETTLEMENT_OUTSIDE_PERIOD";

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

        if (settlement.getStatus() != com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus.PAYOUT_PENDING) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }

        // 정산 행을 먼저 잠근 뒤 원장을 current read 한다. 취소 경로도 이 순서를 공유한다.
        OwnerRevenue revenue = ownerRevenueRepository.findByIdWithPessimisticLock(ownerRevenueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        if (!isEligibleForPeriod(revenue, periodStartAt, periodEndAt)) {
            return false;
        }

        revenue.markSettlementPending(periodEndAt);
        weeklySettlementItemRepository.save(WeeklySettlementItem.create(settlement, revenue));

        return true;
    }

    /**
     * 기간 밖 누락 원장 표시와 운영 실패 이력을 하나의 트랜잭션으로 커밋한다.
     * 과거 버전에서 표시만 남은 행도 이력을 보충해 수습 큐에서 사라지지 않게 한다.
     */
    @Transactional
    public boolean markLateRevenueReported(
            Long ownerRevenueId, LocalDateTime periodStartAt, LocalDateTime periodEndAt
    ) {
        OwnerRevenue revenue = ownerRevenueRepository.findByIdWithPessimisticLock(ownerRevenueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        boolean newlyMarked = revenue.markLateSettlementReported(periodStartAt, LocalDateTime.now());
        boolean alreadyLogged = operationFailureLogRepository.existsByOperationAndRefTypeAndRefIdAndErrorCode(
                LATE_REVENUE_OPERATION, "ownerRevenue", String.valueOf(ownerRevenueId), LATE_REVENUE_ERROR_CODE);
        if (!newlyMarked && alreadyLogged) {
            return false;
        }
        operationFailureLogRepository.save(OperationFailureLog.create(
                OperationFailureCategory.SCHEDULER,
                LATE_REVENUE_OPERATION,
                "ownerRevenue",
                String.valueOf(ownerRevenueId),
                LATE_REVENUE_ERROR_CODE,
                "지난 정산 기간에 포함되지 않은 원장입니다. 별도 정산 수습이 필요합니다.",
                "periodStartAt=" + periodStartAt + ", periodEndAt=" + periodEndAt));
        return true;
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
