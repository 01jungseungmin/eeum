package com.eeum.eeum.application.file.scheduler;

import com.eeum.eeum.application.file.FileObjectLifecycleService;
import com.eeum.eeum.application.file.FileObjectLifecycleService.FileObjectCleanupTarget;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileObjectCleanupScheduler {

    private static final int CONFIRMED_FILE_RETENTION_HOURS = 24;
    // 이 횟수를 넘기면 CLEANUP_FAILED로 내려 재시도를 멈춘다. 무한 재시도를 두면
    // 영구 실패 건이 claimExpiredUnattached의 top-100 앞자리를 계속 차지해 뒤가 굶는다.
    private static final int MAX_CLEANUP_ATTEMPTS = 5;

    private final FileObjectLifecycleService fileObjectLifecycleService;
    private final FileStorageService fileStorageService;
    private final OperationFailureRecorder operationFailureRecorder;

    @Scheduled(cron = "0 15 * * * *")
    @SchedulerLock(name = "cleanupUnattachedFileObjects", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void cleanupUnattachedFiles() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(CONFIRMED_FILE_RETENTION_HOURS);

        List<CleanupFailure> failures = new ArrayList<>();
        for (FileObjectCleanupTarget target : fileObjectLifecycleService.claimExpiredUnattached(threshold)) {
            // S3 삭제와 DB 삭제를 나눠 잡는다. 어느 쪽에서 실패했는지 모르면 S3 객체가
            // 아직 남았는지 이미 지워졌는지 로그만 보고 판단할 수 없다.
            boolean objectDeleted = false;
            try {
                fileStorageService.deleteObject(target.objectKey());
                objectDeleted = true;
                fileObjectLifecycleService.completeCleanup(target.fileObjectId());
            } catch (Exception exception) {
                failures.add(new CleanupFailure(target, exception, objectDeleted));
            }
        }

        failures.forEach(this::handleFailure);
    }

    private void handleFailure(CleanupFailure failure) {
        FileObjectCleanupTarget target = failure.target();

        // 원인은 시도 횟수를 기록하기 전에 남긴다. 기록이 실패해도 원인은 남아야 한다.
        // S3 삭제만 성공했을 수도 있으므로 첨부를 금지한 상태로 다음 회차에 재시도한다.
        log.warn("미연결 S3 이미지 정리 실패: key={}, s3Deleted={}",
                target.objectKey(), failure.objectDeleted(), failure.exception());

        // 개별 객체 문제가 아니라 S3·DB 전면 장애면 시도 횟수를 세지 않는다. 세어 버리면
        // 몇 시간짜리 장애가 정리 큐 전체를 CLEANUP_FAILED로 만들어 영구 방치된다.
        if (isInfrastructureFailure(failure.exception())) {
            return;
        }
        if (!recordFailureQuietly(target)) {
            return;
        }

        log.error("미연결 S3 이미지 정리를 {}회 실패해 중단합니다: key={}, s3Deleted={}",
                MAX_CLEANUP_ATTEMPTS, target.objectKey(), failure.objectDeleted());
        // 아무도 다시 정리하지 않을 객체가 확정된 시점이다. 로그만 남기면 운영자가 모른다.
        operationFailureRecorder.record(
                OperationFailureCategory.SCHEDULER,
                "FILE_OBJECT_CLEANUP",
                "FILE_OBJECT",
                String.valueOf(target.fileObjectId()),
                failure.exception(),
                "objectKey=%s, s3Deleted=%s".formatted(target.objectKey(), failure.objectDeleted()));
    }

    /**
     * 시도 횟수를 올리고 한계 초과 여부를 돌려준다.
     *
     * <p>이 호출도 DB 쓰기다. 여기서 예외가 새어 나가면 남은 실패 대상의 기록과 로그가
     * 통째로 밀리므로 삼킨다 — 실패 이력을 남기려다 정리 자체를 멈추면 주객이 전도된다.
     */
    private boolean recordFailureQuietly(FileObjectCleanupTarget target) {
        try {
            return fileObjectLifecycleService.recordCleanupFailure(
                    target.fileObjectId(), MAX_CLEANUP_ATTEMPTS);
        } catch (Exception exception) {
            log.error("정리 실패 기록에 실패했습니다: fileObjectId={}", target.fileObjectId(), exception);
            return false;
        }
    }

    /**
     * 대상 하나가 아니라 저장소·DB 전체가 죽어서 난 실패인지 본다.
     *
     * <p>전면 장애까지 시도 횟수에 세면 일시적 장애가 영구 유실로 바뀐다. 예를 들어
     * 저장소 설정이 빠지거나 DB가 내려간 채로 다섯 회차(시간당 1회)만 지나면 그 사이
     * CLEANUP_PENDING이던 모든 행이 CLEANUP_FAILED로 내려가 되돌릴 경로가 없다.
     */
    private boolean isInfrastructureFailure(Throwable exception) {
        if (exception instanceof TransientDataAccessException
                || exception instanceof DataAccessResourceFailureException
                || exception instanceof CannotCreateTransactionException) {
            return true;
        }
        if (exception instanceof BusinessException businessException) {
            // SdkClientException은 응답을 받지 못한 경우 — 네트워크 단절, 자격증명 해석 실패 등.
            // 객체별 사유(없는 key 등)는 S3Exception으로 와서 여기 걸리지 않는다.
            return businessException.getErrorCode() == ErrorCode.FILE_STORAGE_NOT_CONFIGURED
                    || businessException.getCause() instanceof SdkClientException;
        }
        return false;
    }

    private record CleanupFailure(
            FileObjectCleanupTarget target,
            Exception exception,
            boolean objectDeleted
    ) {
    }
}
