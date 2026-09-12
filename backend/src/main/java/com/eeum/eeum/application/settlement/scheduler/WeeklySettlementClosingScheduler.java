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
        List<Long> eligibleIds = ownerRevenueRepository.findEligibleIds(OwnerRevenueStatus.ACCRUED, periodEndAt);

        for (Long ownerRevenueId : eligibleIds) {
            try {
                if (weeklySettlementClosingService.closeEligibleRevenue(
                        ownerRevenueId, periodStartAt, periodEndAt)) {
                    // 지난 마감에서 빠진 원장이 이번 주기에 섞여 들어갔다. 조용히 넘기면
                    // 정산 행의 기간과 실제 포함 원장이 어긋난 채로 남는다.
                    recordFailure(ownerRevenueId, "SETTLEMENT_LATE_INCLUSION",
                            "지난 마감에서 누락된 원장이 이번 주기 정산에 포함됨",
                            periodStartAt, periodEndAt);
                }
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

    private String payload(LocalDateTime periodStartAt, LocalDateTime periodEndAt) {
        return "periodStartAt=" + periodStartAt + ", periodEndAt=" + periodEndAt;
    }
}
