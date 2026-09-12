package com.eeum.eeum.application.settlement.scheduler;

import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.settlement.service.WeeklySettlementClosingService;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklySettlementClosingScheduler {

    private final OwnerRevenueRepository ownerRevenueRepository;
    private final WeeklySettlementClosingService weeklySettlementClosingService;
    private final OperationFailureRecorder operationFailureRecorder;

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "closeWeeklySettlements", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void closeWeeklySettlements() {
        LocalDateTime periodEndAt = LocalDateTime.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        LocalDateTime periodStartAt = periodEndAt.minusWeeks(1);
        List<Long> lateIds = ownerRevenueRepository.findLateEligibleIds(OwnerRevenueStatus.ACCRUED, periodStartAt);
        for (Long ownerRevenueId : lateIds) {
            // 기간 밖 원장은 현재 주차에 섞지 않는다. 지급 누락을 숨기지도 않고 운영
            // 수습 대기열에 남겨 별도 재마감 또는 수동 지급 절차를 선택하게 한다.
            recordFailure(ownerRevenueId, "SETTLEMENT_OUTSIDE_PERIOD",
                    "지난 정산 기간에 포함되지 않은 원장입니다. 별도 정산 수습이 필요합니다.",
                    periodStartAt, periodEndAt);
        }
        List<Long> eligibleIds = ownerRevenueRepository.findEligibleIds(
                OwnerRevenueStatus.ACCRUED, periodStartAt, periodEndAt);

        for (Long ownerRevenueId : eligibleIds) {
            try {
                weeklySettlementClosingService.closeEligibleRevenue(ownerRevenueId, periodStartAt, periodEndAt);
            } catch (RuntimeException e) {
                // 돈이 걸린 경로다. 로그만 남기면 마감에서 빠진 원장을 아무도 모른다.
                log.warn("주간 정산 마감 제외: ownerRevenueId={}", ownerRevenueId, e);
                operationFailureRecorder.record(
                        OperationFailureCategory.SCHEDULER,
                        "WeeklySettlementClosingScheduler.closeWeeklySettlements",
                        "ownerRevenue", String.valueOf(ownerRevenueId),
                        e,
                        payload(periodStartAt, periodEndAt));
            }
        }
    }

    private String payload(LocalDateTime periodStartAt, LocalDateTime periodEndAt) {
        return "periodStartAt=" + periodStartAt + ", periodEndAt=" + periodEndAt;
    }

    private void recordFailure(
            Long ownerRevenueId, String code, String message,
            LocalDateTime periodStartAt, LocalDateTime periodEndAt
    ) {
        log.warn("{} — ownerRevenueId={}, period={}~{}", message, ownerRevenueId, periodStartAt, periodEndAt);
        operationFailureRecorder.record(
                OperationFailureCategory.SCHEDULER,
                "WeeklySettlementClosingScheduler.closeWeeklySettlements",
                "ownerRevenue", String.valueOf(ownerRevenueId),
                code, message, payload(periodStartAt, periodEndAt));
    }
}
