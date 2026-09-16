package com.eeum.eeum.application.operation.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 매일 04:00 — 보존 기간(3개월) 지난 운영 실패 이력 물리 삭제.
// 실패 이력은 Soft Delete 대상이 아니며, 무한히 쌓이면 대시보드 조회가 느려진다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureLogCleanupScheduler {

    private static final int RETENTION_MONTHS = 3;

    private final OperationFailureLogRepository operationFailureLogRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "cleanupOldFailureLogs", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanupOldFailureLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        int deleted = operationFailureLogRepository.deleteOlderThan(threshold);
        log.info("[OperationFailureLogCleanup] 오래된 실패 이력 삭제: count={}, threshold={}",
                deleted, threshold);
    }
}
