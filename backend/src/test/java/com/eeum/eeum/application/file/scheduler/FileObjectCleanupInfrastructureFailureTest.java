package com.eeum.eeum.application.file.scheduler;

import com.eeum.eeum.application.file.FileObjectLifecycleService;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 증상: S3 전면 장애가 몇 시간 이어지면 정리 큐 전체가 CLEANUP_FAILED로 내려가 영구 방치된다.
 * 결함 위치: FileObjectCleanupScheduler.isInfrastructureFailure가 SdkClientException만 보던 점.
 * S3는 과부하·내부 오류를 5xx S3Exception으로 돌려주므로 그쪽도 전면 장애로 봐야 한다.
 */
@ExtendWith(MockitoExtension.class)
class FileObjectCleanupInfrastructureFailureTest {

    @Mock private FileObjectLifecycleService lifecycle;
    @Mock private FileStorageService storage;
    @Mock private OperationFailureRecorder operationFailureRecorder;
    @InjectMocks private FileObjectCleanupScheduler scheduler;

    @Test
    void S3_과부하_응답은_시도_횟수에_세지_않는다() {
        // Given — 한 회차에 최대 200건을 지우므로 SlowDown이 충분히 나온다.
        givenTarget();
        doThrow(wrapped(S3Exception.builder().statusCode(503).message("SlowDown").build()))
                .when(storage).deleteObject("used/42/a.png");

        // When
        scheduler.cleanupUnattachedFiles();

        // Then
        verify(lifecycle, never()).recordCleanupFailure(any(), anyInt());
    }

    @Test
    void S3_내부_오류도_시도_횟수에_세지_않는다() {
        // Given
        givenTarget();
        doThrow(wrapped(S3Exception.builder().statusCode(500).message("InternalError").build()))
                .when(storage).deleteObject("used/42/a.png");

        // When
        scheduler.cleanupUnattachedFiles();

        // Then
        verify(lifecycle, never()).recordCleanupFailure(any(), anyInt());
    }

    @Test
    void 객체별_사유는_그대로_시도_횟수를_센다() {
        // Given — 4xx는 이 객체 하나의 문제다. 세지 않으면 재시도 한계가 무의미해진다.
        givenTarget();
        doThrow(wrapped(S3Exception.builder().statusCode(403).message("AccessDenied").build()))
                .when(storage).deleteObject("used/42/a.png");

        // When
        scheduler.cleanupUnattachedFiles();

        // Then
        verify(lifecycle).recordCleanupFailure(1L, 5);
    }

    private void givenTarget() {
        given(lifecycle.claimExpiredUnattached(any())).willReturn(List.of(
                new FileObjectLifecycleService.FileObjectCleanupTarget(1L, "used/42/a.png")));
    }

    // FileStorageService.deleteObject가 SdkException을 감싸는 방식과 같게 만든다.
    private BusinessException wrapped(Throwable cause) {
        return new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 이미지 삭제에 실패했습니다", cause);
    }
}
