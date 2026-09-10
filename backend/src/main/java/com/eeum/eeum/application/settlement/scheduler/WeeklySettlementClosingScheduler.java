package com.eeum.eeum.application.settlement.scheduler;

import com.eeum.eeum.application.settlement.service.WeeklySettlementClosingService;
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

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "closeWeeklySettlements", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void closeWeeklySettlements() {
        LocalDateTime periodEndAt = LocalDateTime.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        LocalDateTime periodStartAt = periodEndAt.minusWeeks(1);
        List<Long> eligibleIds = ownerRevenueRepository.findEligibleIds(OwnerRevenueStatus.ACCRUED, periodEndAt);

        for (Long ownerRevenueId : eligibleIds) {
            try {
                weeklySettlementClosingService.closeEligibleRevenue(ownerRevenueId, periodStartAt, periodEndAt);
            } catch (BusinessException e) {
                log.warn("주간 정산 마감 제외: ownerRevenueId={}", ownerRevenueId, e);
            }
        }
    }
}
