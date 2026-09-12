package com.eeum.eeum.application.settlement.scheduler;

import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.settlement.service.WeeklySettlementClosingService;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklySettlementClosingSchedulerTest {

    @Mock private OwnerRevenueRepository ownerRevenueRepository;
    @Mock private WeeklySettlementClosingService weeklySettlementClosingService;
    @Mock private OperationFailureRecorder operationFailureRecorder;

    @InjectMocks private WeeklySettlementClosingScheduler scheduler;

    @Test
    void 한_원장이_실패해도_나머지_마감은_계속된다() {
        // given
        givenEligible(1L, 2L, 3L);
        when(weeklySettlementClosingService.closeEligibleRevenue(eq(2L), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));

        // when
        scheduler.closeWeeklySettlements();

        // then
        verify(weeklySettlementClosingService).closeEligibleRevenue(eq(1L), any(), any());
        verify(weeklySettlementClosingService).closeEligibleRevenue(eq(3L), any(), any());
    }

    @Test
    void 마감에서_빠진_원장은_운영_실패_이력에_남는다() {
        // given — 돈이 걸린 경로다. 로그만 남기면 누락을 아무도 모른다.
        givenEligible(1L);
        when(weeklySettlementClosingService.closeEligibleRevenue(eq(1L), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));

        // when
        scheduler.closeWeeklySettlements();

        // then
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER),
                eq("WeeklySettlementClosingScheduler.closeWeeklySettlements"),
                eq("ownerRevenue"), eq("1"),
                any(Throwable.class), anyString());
    }

    @Test
    void 지난_마감에서_누락된_원장이_뒤늦게_포함되면_이력에_남는다() {
        // given — 정산 행의 기간과 실제 포함 원장이 어긋난 채로 남으면 안 된다
        givenEligible(1L);
        when(weeklySettlementClosingService.closeEligibleRevenue(eq(1L), any(), any())).thenReturn(true);

        // when
        scheduler.closeWeeklySettlements();

        // then
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER),
                eq("WeeklySettlementClosingScheduler.closeWeeklySettlements"),
                eq("ownerRevenue"), eq("1"),
                eq("SETTLEMENT_LATE_INCLUSION"), anyString(), anyString());
    }

    @Test
    void 제때_마감된_원장은_이력을_남기지_않는다() {
        // given
        givenEligible(1L);
        when(weeklySettlementClosingService.closeEligibleRevenue(eq(1L), any(), any())).thenReturn(false);

        // when
        scheduler.closeWeeklySettlements();

        // then
        verify(operationFailureRecorder, never()).record(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private void givenEligible(Long... ownerRevenueIds) {
        when(ownerRevenueRepository.findEligibleIds(
                eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class)))
                .thenReturn(List.of(ownerRevenueIds));
    }
}
