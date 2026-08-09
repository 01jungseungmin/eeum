package com.eeum.eeum.application.notification.scheduler;

import com.eeum.eeum.application.notification.service.UnreadCountService;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// cleanupOldNotifications  : 매일 새벽 3시 — 6개월 이전 알림 물리 삭제
// recalculateUnreadCounts  : 5분마다 — Redis unread 캐시 ↔ DB 정합성 보정

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private static final int RETENTION_MONTHS = 6;
    private static final String UNREAD_KEY_PREFIX = "unread:account:";

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate redisTemplate;
    private final UnreadCountService unreadCountService;

    // 매일 새벽 3시 — 6개월 이전 알림 일괄 삭제
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        int deleted = notificationRepository.deleteOldNotifications(threshold);
        log.info("[NotificationCleanup] 오래된 알림 삭제: count={}, threshold={}", deleted, threshold);
    }

    // 5분마다 — Redis unread 키 스캔 후 DB 값과 불일치 시 Redis 보정 Redis key 패턴: unread:account:*
    @Scheduled(fixedRate = 300_000)
    public void recalculateUnreadCounts() {
        var keys = redisTemplate.keys(UNREAD_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        int mismatchCount = 0;
        for (String key : keys) {
            try {
                String accountIdStr = key.replace(UNREAD_KEY_PREFIX, "");
                Long accountId = Long.parseLong(accountIdStr);

                String cached = redisTemplate.opsForValue().get(key);
                long redisCount = cached != null ? Long.parseLong(cached) : -1;
                long dbCount = unreadCountService.refreshFromDb(accountId).getUnreadCount();

                if (dbCount != redisCount) {
                    mismatchCount++;
                    log.debug("[UnreadReconcile] 보정: accountId={}, redis={}, db={}", accountId, redisCount, dbCount);
                }
            } catch (Exception e) {
                log.warn("[UnreadReconcile] 보정 실패: key={}, error={}", key, e.getMessage());
            }
        }
        if (mismatchCount > 0) {
            log.info("[UnreadReconcile] 불일치 보정 완료: count={}", mismatchCount);
        }
    }
}
