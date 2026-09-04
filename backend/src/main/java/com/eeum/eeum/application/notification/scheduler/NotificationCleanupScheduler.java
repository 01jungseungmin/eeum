package com.eeum.eeum.application.notification.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.application.notification.service.UnreadCountService;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cleanupOldNotifications  : 매일 새벽 3시 — 6개월 이전 알림 물리 삭제
// recalculateUnreadCounts  : 5분마다 — Redis unread 캐시 ↔ DB 정합성 보정

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private static final int RETENTION_MONTHS = 6;
    private static final String UNREAD_KEY_PREFIX = "unread:account:";
    private static final int SCAN_BATCH = 500;
    // 한 번에 집계·보정할 계정 수. 배치가 커지면 IN 절과 보정 구간이 함께 길어지고,
    // 그동안 이 스레드를 공유하는 다른 스케줄러(1초 주기 Outbox 포함)가 대기한다.
    private static final int RECONCILE_BATCH = 200;

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate redisTemplate;
    private final UnreadCountService unreadCountService;

    // 매일 새벽 3시 — 6개월 이전 알림 일괄 삭제
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "cleanupOldNotifications", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        int deleted = notificationRepository.deleteOldNotifications(threshold);
        log.info("[NotificationCleanup] 오래된 알림 삭제: count={}, threshold={}", deleted, threshold);
    }

    /**
     * 5분마다 — Redis unread 캐시를 DB 기준으로 보정한다 (키 패턴: {@code unread:account:*}).
     *
     * <p>키 순회는 SCAN으로 한다. {@code KEYS}는 매칭이 끝날 때까지 Redis 전체를 블로킹해,
     * 그동안 이 서버의 모든 Redis 명령(세션 검증·락·캐시)이 함께 멈춘다.
     *
     * <p>집계는 계정 단위가 아니라 배치 단위로 한 번에 한다. 그리고 <b>불일치한 계정만</b>
     * 재계산한다. 예전에는 캐시가 살아 있는 계정 전부에 대해 재계산을 돌렸는데,
     * 재계산은 계정 행 비관적 락을 잡으므로 5분마다 전 계정 행을 차례로 잠그는 셈이었다
     * (자원 예산 문서의 금지 패턴 7). 대부분의 계정은 애초에 값이 맞다.
     *
     * <p>이 메서드는 풀 크기 1인 스케줄러 스레드에서 돈다. 여기서 오래 머물면 1초 주기
     * Outbox 스케줄러가 그동안 멈춰 알림 생성 자체가 지연된다 — 배치 크기를 키울 때
     * 반드시 함께 고려한다.
     */
    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = "recalculateUnreadCounts", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    public void recalculateUnreadCounts() {
        List<Long> batch = new ArrayList<>(RECONCILE_BATCH);
        int mismatchCount = 0;

        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(UNREAD_KEY_PREFIX + "*").count(SCAN_BATCH).build())) {
            while (cursor.hasNext()) {
                parseAccountId(cursor.next()).ifPresent(batch::add);
                if (batch.size() >= RECONCILE_BATCH) {
                    mismatchCount += reconcileBatch(batch);
                    batch.clear();
                }
            }
        } catch (Exception e) {
            log.warn("[UnreadReconcile] 키 스캔 실패: {}", e.getMessage());
            return;
        }
        mismatchCount += reconcileBatch(batch);

        if (mismatchCount > 0) {
            log.info("[UnreadReconcile] 불일치 보정 완료: count={}", mismatchCount);
        }
    }

    // 캐시값과 DB값을 비교해 어긋난 계정만 재계산한다.
    private int reconcileBatch(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return 0;
        }

        Map<Long, Long> dbCounts;
        List<String> cachedValues;
        try {
            dbCounts = notificationRepository.countUnreadByAccountIds(accountIds);
            cachedValues = redisTemplate.opsForValue().multiGet(
                    accountIds.stream().map(id -> UNREAD_KEY_PREFIX + id).toList());
        } catch (Exception e) {
            log.warn("[UnreadReconcile] 배치 집계 실패 — 이번 주기 건너뜀: size={}, error={}",
                    accountIds.size(), e.getMessage());
            return 0;
        }

        int corrected = 0;
        for (int i = 0; i < accountIds.size(); i++) {
            Long accountId = accountIds.get(i);
            // 스캔 이후 키가 지워졌으면 보정할 대상이 아니다 — 다음 조회가 DB에서 복구한다.
            String cached = cachedValues == null || i >= cachedValues.size() ? null : cachedValues.get(i);
            if (cached == null) {
                continue;
            }

            try {
                long redisCount = Long.parseLong(cached);
                long dbCount = dbCounts.getOrDefault(accountId, 0L);
                if (redisCount == dbCount) {
                    continue;
                }

                // 여기서만 계정 행을 잠근다. 재계산이 전체·카테고리 캐시를 함께 갈아끼운다.
                unreadCountService.refreshFromDb(accountId);
                corrected++;
                log.debug("[UnreadReconcile] 보정: accountId={}, redis={}, db={}",
                        accountId, redisCount, dbCount);
            } catch (NumberFormatException e) {
                // 값이 깨졌으면 비교가 불가능하다. 지워 두면 다음 조회가 DB에서 복구한다.
                log.warn("[UnreadReconcile] 캐시값 형식 오류 — 무효화: accountId={}", accountId);
                unreadCountService.invalidateSnapshot(accountId);
            } catch (Exception e) {
                log.warn("[UnreadReconcile] 보정 실패: accountId={}, error={}", accountId, e.getMessage());
            }
        }
        return corrected;
    }

    private java.util.Optional<Long> parseAccountId(String key) {
        try {
            return java.util.Optional.of(Long.parseLong(key.substring(UNREAD_KEY_PREFIX.length())));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            log.warn("[UnreadReconcile] 키 형식 오류로 건너뜀: key={}", key);
            return java.util.Optional.empty();
        }
    }
}
