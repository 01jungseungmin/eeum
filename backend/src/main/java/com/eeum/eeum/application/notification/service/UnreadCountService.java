package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * unread 카운트 조회/갱신 전담 컴포넌트.
 * NotificationService에서 분리해 self-invocation 없이 프록시를 통해 호출되도록 하여
 * AFTER_COMMIT 호출도 REQUIRES_NEW 트랜잭션에서 Account 행 잠금을 획득한다.
 *
 * Redis 키:
 * - unread:account:{accountId}  — 전체 미읽음 수
 * - unread:category:{accountId} — 카테고리별 미읽음 수 hash
 * 두 키는 DB 스냅샷을 기준으로 Lua에서 원자적으로 함께 교체한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private static final String UNREAD_KEY_PREFIX = "unread:account:";
    private static final String CATEGORY_KEY_PREFIX = "unread:category:";
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> READ_UNREAD_SNAPSHOT = new DefaultRedisScript<>(
            "local total = redis.call('get', KEYS[1]) "
                    + "if not total then return {} end "
                    + "local result = {total} "
                    + "local categories = redis.call('hgetall', KEYS[2]) "
                    + "for i = 1, #categories do table.insert(result, categories[i]) end "
                    + "return result",
            List.class);
    private static final DefaultRedisScript<Long> REPLACE_UNREAD_SNAPSHOT = new DefaultRedisScript<>(
            "redis.call('set', KEYS[1], ARGV[1]) "
                    + "redis.call('del', KEYS[2]) "
                    + "for i = 2, #ARGV, 2 do redis.call('hset', KEYS[2], ARGV[i], ARGV[i + 1]) end "
                    + "return 1",
            Long.class);

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    // ===================== 조회 =====================

    // 전체·카테고리 캐시가 모두 완성된 경우에만 캐시를 사용한다.
    // 하나라도 미스면 Account 행을 잠근 뒤 동일 DB 스냅샷으로 두 캐시를 함께 복구한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UnreadCountResponseDto getUnreadCount(Long accountId) {
        List<?> cachedSnapshot = redisTemplate.execute(
                READ_UNREAD_SNAPSHOT,
                List.of(totalKey(accountId), categoryKey(accountId)));
        CachedUnreadSnapshot cached = parseCachedSnapshot(cachedSnapshot);

        if (cached != null && hasAllCategories(cached.categoryCounts())) {
            return UnreadCountResponseDto.of(
                    cached.total(), parseCategoryCounts(cached.categoryCounts()));
        }

        return rebuildFromDbWithAccountLock(accountId);
    }

    private CachedUnreadSnapshot parseCachedSnapshot(List<?> snapshot) {
        if (snapshot == null || snapshot.isEmpty() || snapshot.size() % 2 == 0) return null;

        try {
            long total = Long.parseLong(snapshot.get(0).toString());
            Map<Object, Object> categoryCounts = new java.util.HashMap<>();
            for (int i = 1; i < snapshot.size(); i += 2) {
                categoryCounts.put(snapshot.get(i), snapshot.get(i + 1));
            }
            return new CachedUnreadSnapshot(total, categoryCounts);
        } catch (RuntimeException e) {
            log.warn("unread 캐시 스냅샷 파싱 실패 — DB에서 복구", e);
            return null;
        }
    }

    private Map<NotificationCategory, Long> parseCategoryCounts(Map<Object, Object> cached) {
        Map<NotificationCategory, Long> result = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            result.put(category, 0L);
        }
        for (Map.Entry<Object, Object> entry : cached.entrySet()) {
            try {
                result.put(NotificationCategory.valueOf(entry.getKey().toString()),
                        Long.parseLong(entry.getValue().toString()));
            } catch (IllegalArgumentException e) {
                log.warn("unread 카테고리 캐시 파싱 실패 — 무시: field={}", entry.getKey());
            }
        }
        return result;
    }

    // ===================== 갱신 (변경 트랜잭션 커밋 후 호출 전제) =====================

    // increment/decrement와 DB count SET을 혼용하면 afterCommit 실행 순서가 뒤집힐 때 캐시가 틀어진다.
    // 모든 변경 경로는 Account 행을 동일 mutex로 잠그고 현재 DB 상태로 전체 스냅샷을 재작성한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(Long accountId) {
        rebuildFromDbWithAccountLock(accountId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrement(Long accountId) {
        rebuildFromDbWithAccountLock(accountId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(Long accountId) {
        rebuildFromDbWithAccountLock(accountId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UnreadCountResponseDto refreshFromDb(Long accountId) {
        return rebuildFromDbWithAccountLock(accountId);
    }

    public void invalidateSnapshot(Long accountId) {
        redisTemplate.delete(List.of(totalKey(accountId), categoryKey(accountId)));
    }

    private UnreadCountResponseDto rebuildFromDbWithAccountLock(Long accountId) {
        accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        long dbCount = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
        Map<NotificationCategory, Long> categoryCounts = emptyCategoryCounts();
        categoryCounts.putAll(notificationRepository.countUnreadByCategory(accountId));

        List<String> snapshotArgs = new ArrayList<>();
        snapshotArgs.add(String.valueOf(dbCount));
        categoryCounts.forEach((category, count) -> {
            snapshotArgs.add(category.name());
            snapshotArgs.add(String.valueOf(count));
        });
        redisTemplate.execute(
                REPLACE_UNREAD_SNAPSHOT,
                List.of(totalKey(accountId), categoryKey(accountId)),
                snapshotArgs.toArray());

        log.debug("unread 캐시 동기화: accountId={}, count={}", accountId, dbCount);
        return UnreadCountResponseDto.of(dbCount, categoryCounts);
    }

    private Map<NotificationCategory, Long> emptyCategoryCounts() {
        Map<NotificationCategory, Long> counts = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            counts.put(category, 0L);
        }
        return counts;
    }

    private boolean hasAllCategories(Map<Object, Object> cached) {
        if (cached.size() != NotificationCategory.values().length) return false;
        for (NotificationCategory category : NotificationCategory.values()) {
            if (!cached.containsKey(category.name())) return false;
        }
        return true;
    }

    private String totalKey(Long accountId) {
        return UNREAD_KEY_PREFIX + accountId;
    }

    private String categoryKey(Long accountId) {
        return CATEGORY_KEY_PREFIX + accountId;
    }

    private record CachedUnreadSnapshot(long total, Map<Object, Object> categoryCounts) {
    }
}
