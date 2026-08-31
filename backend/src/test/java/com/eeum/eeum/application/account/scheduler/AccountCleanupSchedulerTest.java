package com.eeum.eeum.application.account.scheduler;

import com.eeum.eeum.application.account.service.AccountCleanupService;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 계정 개인정보 파기 스케줄러 테스트.
 * 한 계정의 실패가 회차 전체를 죽이지 않아야 한다 —
 * 하나로 묶으면 계정 하나 때문에 정상 처리 가능한 계정까지 함께 롤백된다.
 */
@ExtendWith(MockitoExtension.class)
class AccountCleanupSchedulerTest {

    @Mock private AccountCleanupService accountCleanupService;
    @Mock private OperationFailureRecorder operationFailureRecorder;

    @InjectMocks
    private AccountCleanupScheduler accountCleanupScheduler;

    @Test
    void 한_계정이_실패해도_나머지는_파기한다() {
        // given
        givenTargets(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("파기 실패"))
                .when(accountCleanupService).anonymizeAccount(2L);

        // when
        accountCleanupScheduler.cleanupWithdrawnAccounts();

        // then
        verify(accountCleanupService).anonymizeAccount(1L);
        verify(accountCleanupService).anonymizeAccount(3L);
    }

    @Test
    void 파기_실패는_운영_실패_이력으로_남긴다() {
        // given — 로그만 남기면 매일 같은 실패가 반복돼도 아무도 모른다
        givenTargets(List.of(2L));
        doThrow(new RuntimeException("파기 실패"))
                .when(accountCleanupService).anonymizeAccount(2L);

        // when
        accountCleanupScheduler.cleanupWithdrawnAccounts();

        // then
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER),
                eq("ACCOUNT_CLEANUP"),
                eq("ACCOUNT"),
                eq("2"),
                any(Throwable.class),
                eq(null));
    }

    @Test
    void 대상이_없으면_파기도_이력도_없다() {
        givenTargets(List.of());

        accountCleanupScheduler.cleanupWithdrawnAccounts();

        verify(accountCleanupService, never()).anonymizeAccount(any());
        verifyNoInteractions(operationFailureRecorder);
    }

    @Test
    void 대상을_ID_커서로_나눠_읽는다() {
        // given — 한 번에 다 읽으면 backlog가 쌓였을 때 조회만으로 잠금 보유 시간을 밀어낸다.
        // 다음 배치는 직전 배치의 마지막 ID 다음부터 읽어야 같은 계정을 반복하지 않는다.
        when(accountCleanupService.findAnonymizeTargetIds(eq(0L), anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(accountCleanupService.findAnonymizeTargetIds(eq(2L), anyInt()))
                .thenReturn(List.of(5L));
        when(accountCleanupService.findAnonymizeTargetIds(eq(5L), anyInt()))
                .thenReturn(List.of());

        // when
        accountCleanupScheduler.cleanupWithdrawnAccounts();

        // then
        verify(accountCleanupService).anonymizeAccount(1L);
        verify(accountCleanupService).anonymizeAccount(2L);
        verify(accountCleanupService).anonymizeAccount(5L);
    }

    @Test
    void 실패한_계정도_커서를_넘겨_같은_회차에서_반복하지_않는다() {
        // given — 실패는 anonymizedAt을 남기지 않아 조건상 계속 대상이다.
        // 커서를 넘기지 않으면 같은 배치를 무한히 다시 읽는다.
        when(accountCleanupService.findAnonymizeTargetIds(eq(0L), anyInt()))
                .thenReturn(List.of(7L));
        when(accountCleanupService.findAnonymizeTargetIds(eq(7L), anyInt()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("파기 실패"))
                .when(accountCleanupService).anonymizeAccount(7L);

        // when
        accountCleanupScheduler.cleanupWithdrawnAccounts();

        // then
        verify(accountCleanupService, times(1)).anonymizeAccount(7L);
    }

    private void givenTargets(List<Long> ids) {
        when(accountCleanupService.findAnonymizeTargetIds(eq(0L), anyInt())).thenReturn(ids);
        if (!ids.isEmpty()) {
            when(accountCleanupService.findAnonymizeTargetIds(
                    eq(ids.get(ids.size() - 1)), anyInt())).thenReturn(List.of());
        }
    }
}
