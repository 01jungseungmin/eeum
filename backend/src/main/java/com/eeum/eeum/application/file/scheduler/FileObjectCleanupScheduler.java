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
                fileObjectLifecycleService.cancelCleanup(target.fileObjectId());
                log.warn("미연결 S3 이미지 정리 실패: key={}", target.objectKey(), exception);
            }
        });
    }
}
