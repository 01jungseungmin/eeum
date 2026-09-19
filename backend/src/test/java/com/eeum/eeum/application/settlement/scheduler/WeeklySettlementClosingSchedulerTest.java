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

/** 누락 정산 수습의 한 건 실패가 이후 마감을 중단하지 않게 고정한다.
 *
 * 수습 표시는 원장별 예외 격리와 실패 이력 기록을 통해 나머지 원장과 분리한다.
 */
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
    void 기간을_지난_누락_원장은_현재_주차에_넣지_않고_수습_이력으로_남긴다() {
        // given
        when(ownerRevenueRepository.findLateEligibleIds(eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class)))
                .thenReturn(List.of(9L));
        when(weeklySettlementClosingService.markLateRevenueReported(eq(9L), any(LocalDateTime.class)))
                .thenReturn(true);

        // when
        scheduler.closeWeeklySettlements();

        // then
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER),
                eq("WeeklySettlementClosingScheduler.closeWeeklySettlements"),
                eq("ownerRevenue"), eq("9"),
                eq("SETTLEMENT_OUTSIDE_PERIOD"), anyString(), anyString());
        verify(weeklySettlementClosingService, never()).closeEligibleRevenue(eq(9L), any(), any());
    }

    @Test
    void 누락_원장_하나의_수습_실패가_정상_마감을_중단시키지_않는다() {
        // 기간 밖 원장 표시가 예외를 전파하면 이후 누락 원장과 이번 주 정상 마감이 모두 중단됐다.
        // 원장별 수습 실패는 이력으로 격리하고 뒤의 원장 마감은 계속해야 한다.
        when(ownerRevenueRepository.findLateEligibleIds(eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class)))
                .thenReturn(List.of(9L, 10L));
        when(ownerRevenueRepository.findEligibleIds(
                eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        when(weeklySettlementClosingService.markLateRevenueReported(eq(9L), any(LocalDateTime.class)))
                .thenThrow(new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));

        scheduler.closeWeeklySettlements();

        verify(weeklySettlementClosingService).markLateRevenueReported(eq(10L), any(LocalDateTime.class));
        verify(weeklySettlementClosingService).closeEligibleRevenue(eq(1L), any(), any());
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER),
                eq("WeeklySettlementClosingScheduler.closeWeeklySettlements"),
                eq("ownerRevenue"), eq("9"), any(Throwable.class), anyString());
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

    @Test
    void 이미_수습_이력으로_표시한_누락_원장도_실패_이력_유실을_막기_위해_다시_기록한다() {
        // given
        when(ownerRevenueRepository.findLateEligibleIds(eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class)))
                .thenReturn(List.of(9L));
        when(weeklySettlementClosingService.markLateRevenueReported(eq(9L), any(LocalDateTime.class)))
                .thenReturn(false);

        // when
        scheduler.closeWeeklySettlements();

        // then
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER), anyString(), anyString(), eq("9"),
                eq("SETTLEMENT_OUTSIDE_PERIOD"), anyString(), anyString());
    }

    private void givenEligible(Long... ownerRevenueIds) {
        when(ownerRevenueRepository.findLateEligibleIds(
                eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(ownerRevenueRepository.findEligibleIds(
                eq(OwnerRevenueStatus.ACCRUED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(ownerRevenueIds));
    }
}
