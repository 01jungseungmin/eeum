package com.eeum.eeum.application.file.scheduler;

import com.eeum.eeum.application.file.FileObjectLifecycleService;
import com.eeum.eeum.application.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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

    @Scheduled(cron = "0 15 * * * *")
    @SchedulerLock(name = "cleanupUnattachedFileObjects", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void cleanupUnattachedFiles() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(CONFIRMED_FILE_RETENTION_HOURS);
        fileObjectLifecycleService.claimExpiredUnattached(threshold).forEach(target -> {
            try {
                fileStorageService.deleteObject(target.objectKey());
                fileObjectLifecycleService.completeCleanup(target.fileObjectId());
            } catch (Exception exception) {
                // S3 삭제만 성공했을 수도 있으므로 첨부를 금지한 상태로 다음 회차에 재시도한다.
                // 다만 한계를 넘기면 포기한다 — 영구 실패 건이 정리 큐를 막지 않게 한다.
                boolean givenUp = fileObjectLifecycleService.recordCleanupFailure(
                        target.fileObjectId(), MAX_CLEANUP_ATTEMPTS);
                if (givenUp) {
                    log.error("미연결 S3 이미지 정리를 {}회 실패해 중단합니다: key={}",
                            MAX_CLEANUP_ATTEMPTS, target.objectKey(), exception);
                } else {
                    log.warn("미연결 S3 이미지 정리 실패: key={}", target.objectKey(), exception);
                }
            }
        });
    }
}
