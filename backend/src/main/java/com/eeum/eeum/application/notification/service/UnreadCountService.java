package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * unread 카운트 조회/무효화 전담 — <b>Redis만 만진다.</b>
 *
 * <p>DB를 읽어 캐시를 다시 쓰는 일은 {@link UnreadSnapshotRebuilder}에 있다.
 * 트랜잭션이 필요한 쪽을 그쪽으로 몰아 두면, 캐시 히트로 끝나는 조회는
 * EntityManager도 DB 커넥션도 건드리지 않는다. 알림 배지 조회는 웹 대시보드가
 * 페이지마다 부르는 경로라 이 차이가 그대로 커넥션 풀 여유가 된다.
 *
 * <p>Redis 키:
 * <ul>
 *   <li>{@code unread:account:{accountId}} — 전체 미읽음 수</li>
 *   <li>{@code unread:category:{accountId}} — 카테고리별 미읽음 수 hash</li>
 * </ul>
 * 두 키는 항상 같은 DB 스냅샷으로 함께 교체된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCountService {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> READ_UNREAD_SNAPSHOT = new DefaultRedisScript<>(
            "local total = redis.call('get', KEYS[1]) "
                    + "if not total then return {} end "
                    + "local result = {total} "
                    + "local categories = redis.call('hgetall', KEYS[2]) "
                    + "for i = 1, #categories do table.insert(result, categories[i]) end "
                    + "return result",
            List.class);

    private final UnreadSnapshotRebuilder snapshotRebuilder;
    private final StringRedisTemplate redisTemplate;

    // ===================== 조회 =====================

    // 전체·카테고리 캐시가 모두 완성된 경우에만 캐시를 사용한다.
    // 하나라도 미스면 Account 행을 잠근 뒤 동일 DB 스냅샷으로 두 캐시를 함께 복구한다.
    public UnreadCountResponseDto getUnreadCount(Long accountId) {
        List<?> cachedSnapshot = redisTemplate.execute(
                READ_UNREAD_SNAPSHOT,
                List.of(UnreadCacheKeys.total(accountId), UnreadCacheKeys.category(accountId)));
        CachedUnreadSnapshot cached = parseCachedSnapshot(cachedSnapshot);

        if (cached != null && hasAllCategories(cached.categoryCounts())) {
            return UnreadCountResponseDto.of(
                    cached.total(), parseCategoryCounts(cached.categoryCounts()));
        }

        return snapshotRebuilder.rebuild(accountId);
    }

    private CachedUnreadSnapshot parseCachedSnapshot(List<?> snapshot) {
        if (snapshot == null || snapshot.isEmpty() || snapshot.size() % 2 == 0) return null;

        try {
            long total = Long.parseLong(snapshot.get(0).toString());
            Map<Object, Object> categoryCounts = new HashMap<>();
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

    // ===================== 갱신 =====================

    // increment/decrement와 DB count SET을 혼용하면 실행 순서가 뒤집힐 때 캐시가 틀어진다.
    // 모든 변경 경로는 Account 행을 동일 mutex로 잠그고 현재 DB 상태로 전체 스냅샷을 재작성한다.
    public UnreadCountResponseDto refreshFromDb(Long accountId) {
        return snapshotRebuilder.rebuild(accountId);
    }

    public void increment(Long accountId) {
        snapshotRebuilder.rebuild(accountId);
    }

    public void decrement(Long accountId) {
        snapshotRebuilder.rebuild(accountId);
    }

    public void clear(Long accountId) {
        snapshotRebuilder.rebuild(accountId);
    }

    // ===================== 무효화 =====================

    // 재계산을 다른 스레드로 넘기기 직전에 부른다. 캐시를 비워 두면 재계산이 폐기되거나
    // 실패하더라도 다음 조회가 캐시 미스로 DB에서 정확한 값을 복구한다.
    // 반대로 낡은 값을 그대로 두면 "완성됐지만 틀린" 스냅샷이라 조회가 계속 그것을 믿는다.
    public void invalidateSnapshot(Long accountId) {
        redisTemplate.delete(
                List.of(UnreadCacheKeys.total(accountId), UnreadCacheKeys.category(accountId)));
    }

    // 참여자 전원 무효화 — 계정 수만큼 왕복하지 않도록 키를 한 번에 넘긴다
    public void invalidateSnapshots(List<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return;

        List<String> keys = new ArrayList<>(accountIds.size() * 2);
        for (Long accountId : accountIds) {
            keys.add(UnreadCacheKeys.total(accountId));
            keys.add(UnreadCacheKeys.category(accountId));
        }
        redisTemplate.delete(keys);
    }

    private boolean hasAllCategories(Map<Object, Object> cached) {
        if (cached.size() != NotificationCategory.values().length) return false;
        for (NotificationCategory category : NotificationCategory.values()) {
            if (!cached.containsKey(category.name())) return false;
        }
        return true;
    }

    private record CachedUnreadSnapshot(long total, Map<Object, Object> categoryCounts) {
    }
}
