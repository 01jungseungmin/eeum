package com.eeum.eeum.application.file.scheduler;

import com.eeum.eeum.application.file.FileObjectLifecycleService;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 증상: S3 삭제 후 DB 실패 시 삭제된 객체를 다시 첨부할 수 있다.
 * 결함 위치: FileObjectCleanupScheduler.cleanupUnattachedFiles.
 * 실패한 대상은 CLEANUP_PENDING을 유지해 재시도하며 첨부 가능 상태로 복원하지 않는다.
 *
 * <p>다만 무한히 재시도하면 영구 실패 건이 claimExpiredUnattached의 top-100 앞자리를
 * 계속 차지해 뒤의 정리 작업이 굶는다. 재시도 한계를 함께 고정한다. 반대로 전면 장애까지
 * 세면 몇 시간짜리 장애가 큐 전체를 CLEANUP_FAILED로 만들므로, 그 구분도 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class FileObjectCleanupSchedulerTest {

    private static final int MAX_CLEANUP_ATTEMPTS = 5;

    @Mock private FileObjectLifecycleService lifecycle;
    @Mock private FileStorageService storage;
    @Mock private OperationFailureRecorder operationFailureRecorder;
    @InjectMocks private FileObjectCleanupScheduler scheduler;

    @Test
    void 정리_실패시_첨부_가능_상태로_되돌리지_않는다() {
        // Given — DB 장애로 완료 처리만 실패한다.
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(1L, "used/42/a.png")));
        doThrow(new DataAccessResourceFailureException("DB unavailable"))
                .when(lifecycle).completeCleanup(1L);

        // When
        scheduler.cleanupUnattachedFiles();

        // Then — 되돌리는 호출(cancelCleanup 류)이 없어야 한다.
        verify(lifecycle).claimExpiredUnattached(any());
        verify(lifecycle).completeCleanup(1L);
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    void 정리_실패는_시도_횟수와_함께_기록해_한계를_넘기면_포기한다() {
        // Given — 이 객체만 계속 실패한다. 전면 장애가 아니므로 시도 횟수를 센다.
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(7L, "used/42/b.png")));
        doThrow(new IllegalStateException("S3 unavailable"))
                .when(storage).deleteObject("used/42/b.png");
        given(lifecycle.recordCleanupFailure(7L, MAX_CLEANUP_ATTEMPTS)).willReturn(true);

        // When
        scheduler.cleanupUnattachedFiles();

        // Then — 삭제가 실패했으므로 완료 처리는 하지 않고, 실패만 기록한다.
        verify(lifecycle).claimExpiredUnattached(any());
        verify(lifecycle).recordCleanupFailure(7L, MAX_CLEANUP_ATTEMPTS);
        verifyNoMoreInteractions(lifecycle);
        // 아무도 다시 정리하지 않을 객체다 — 로그만 남기면 운영자가 볼 방법이 없다.
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.SCHEDULER),
                eq("FILE_OBJECT_CLEANUP"),
                eq("FILE_OBJECT"),
                eq("7"),
                any(Throwable.class),
                any(String.class));
    }

    @Test
    void 저장소_전면_장애는_시도_횟수에_세지_않는다() {
        // Given — 저장소 설정 누락은 개별 객체 문제가 아니다. 세면 장애가 이어지는 동안
        // 큐 전체가 CLEANUP_FAILED로 내려가 되돌릴 경로가 없어진다.
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(9L, "used/42/c.png")));
        doThrow(new BusinessException(ErrorCode.FILE_STORAGE_NOT_CONFIGURED))
                .when(storage).deleteObject("used/42/c.png");

        // When
        scheduler.cleanupUnattachedFiles();

        // Then
        verify(lifecycle, never()).recordCleanupFailure(any(), anyInt());
        verifyNoInteractions(operationFailureRecorder);
    }

    @Test
    void 실패_기록이_또_실패해도_남은_대상을_계속_처리한다() {
        // Given — 실패 기록도 DB 쓰기다. 여기서 예외가 새면 뒤 대상이 통째로 밀린다.
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(1L, "used/42/a.png"),
                new FileObjectLifecycleService.FileObjectCleanupTarget(2L, "used/42/b.png")));
        doThrow(new IllegalStateException("S3 unavailable"))
                .when(storage).deleteObject(any(String.class));
        given(lifecycle.recordCleanupFailure(1L, MAX_CLEANUP_ATTEMPTS))
                .willThrow(new DataAccessResourceFailureException("DB unavailable"));

        // When
        scheduler.cleanupUnattachedFiles();

        // Then — 첫 대상의 기록이 터져도 두 번째 대상까지 기록을 시도한다.
        verify(lifecycle).recordCleanupFailure(1L, MAX_CLEANUP_ATTEMPTS);
        verify(lifecycle).recordCleanupFailure(2L, MAX_CLEANUP_ATTEMPTS);
        // 한계에 닿지 않았으므로 운영 실패 이력은 남기지 않는다.
        verifyNoInteractions(operationFailureRecorder);
    }
}
