package com.eeum.eeum.application.file.scheduler;

import com.eeum.eeum.application.file.FileObjectLifecycleService;
import com.eeum.eeum.application.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 증상: S3 삭제 후 DB 실패 시 삭제된 객체를 다시 첨부할 수 있다.
 * 결함 위치: FileObjectCleanupScheduler.cleanupUnattachedFiles.
 * 실패한 대상은 CLEANUP_PENDING을 유지해 재시도하며 첨부 가능 상태로 복원하지 않는다.
 *
 * <p>다만 무한히 재시도하면 영구 실패 건이 claimExpiredUnattached의 top-100 앞자리를
 * 계속 차지해 뒤의 정리 작업이 굶는다. 재시도 한계를 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class FileObjectCleanupSchedulerTest {

    @Mock private FileObjectLifecycleService lifecycle;
    @Mock private FileStorageService storage;
    @InjectMocks private FileObjectCleanupScheduler scheduler;

    @Test
    void DB_정리_실패시_첨부_가능_상태로_되돌리지_않는다() {
        // Given
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(1L, "used/42/a.png")));
        doThrow(new DataAccessResourceFailureException("DB unavailable"))
                .when(lifecycle).completeCleanup(1L);

        // When
        scheduler.cleanupUnattachedFiles();

        // Then — 되돌리는 호출(cancelCleanup 류)이 없어야 한다. 실패 기록만 남긴다.
        verify(lifecycle).claimExpiredUnattached(any());
        verify(lifecycle).completeCleanup(1L);
        verify(lifecycle).recordCleanupFailure(eq(1L), anyInt());
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    void 정리_실패는_시도_횟수와_함께_기록해_한계를_넘기면_포기한다() {
        // Given — S3 삭제 자체가 계속 실패하는 대상.
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(7L, "used/42/b.png")));
        doThrow(new IllegalStateException("S3 unavailable"))
                .when(storage).deleteObject("used/42/b.png");
        given(lifecycle.recordCleanupFailure(eq(7L), anyInt())).willReturn(true);

        // When
        scheduler.cleanupUnattachedFiles();

        // Then — 삭제가 실패했으므로 완료 처리는 하지 않고, 실패만 기록한다.
        verify(lifecycle).claimExpiredUnattached(any());
        verify(lifecycle).recordCleanupFailure(eq(7L), anyInt());
        verifyNoMoreInteractions(lifecycle);
    }
}
