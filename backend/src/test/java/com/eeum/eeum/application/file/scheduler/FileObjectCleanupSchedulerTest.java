package com.eeum.eeum.application.file.scheduler;

import com.eeum.eeum.application.file.FileObjectLifecycleService;
import com.eeum.eeum.application.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * 증상: S3 삭제 후 DB 실패 시 삭제된 객체를 다시 첨부할 수 있다.
 * 결함 위치: FileObjectCleanupScheduler.cleanupUnattachedFiles.
 * 실패한 대상은 CLEANUP_PENDING을 유지해 재시도하며 첨부 가능 상태로 복원하지 않는다.
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
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("DB unavailable"))
                .when(lifecycle).completeCleanup(1L);
        // When
        scheduler.cleanupUnattachedFiles();
        // Then
        verify(lifecycle).claimExpiredUnattached(any());
        verify(lifecycle).completeCleanup(1L);
        verifyNoMoreInteractions(lifecycle);
    }
}
