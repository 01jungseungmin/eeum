package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * unread 카운트 조회/갱신 전담 컴포넌트.
 * NotificationService에서 분리해 self-invocation 없이 프록시를 통해 호출되도록 하여
 * 조회 시 @Transactional(readOnly = true) 경계가 항상 적용된다.
 *
 * Redis 키:
 * - unread:account:{accountId}  — 전체 미읽음 수 (INCR/DECR로 유지, 미스 시 DB 복구)
 * - unread:category:{accountId} — 카테고리별 미읽음 수 hash (변경 시 무효화, 미스 시 DB 재집계)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private static final String UNREAD_KEY_PREFIX = "unread:account:";
    private static final String CATEGORY_KEY_PREFIX = "unread:category:";

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate redisTemplate;

    // 0보다 클 때만 DECR — GET→검사→DECR로 나누면 동시 읽음 처리 시 둘 다 검사를 통과해 -1이 될 수 있으므로
    // Lua로 원자적으로 처리한다.
    private static final DefaultRedisScript<Long> DECR_IF_POSITIVE = new DefaultRedisScript<>(
            "local v = tonumber(redis.call('get', KEYS[1]) or '0') "
                    + "if v > 0 then return redis.call('decr', KEYS[1]) else return 0 end",
            Long.class);

    // ===================== 조회 =====================

    // 전체는 Redis 우선(미스 시 DB 복구), 카테고리별은 hash 캐시 우선(미스 시 DB 집계 후 캐싱)
    @Transactional(readOnly = true)
    public UnreadCountResponseDto getUnreadCount(Long accountId) {
        String key = UNREAD_KEY_PREFIX + accountId;
        String cached = redisTemplate.opsForValue().get(key);

        long count;
        if (cached != null) {
            count = Long.parseLong(cached);
        } else {
            count = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
            redisTemplate.opsForValue().set(key, String.valueOf(count));
            log.debug("unread 캐시 복구: accountId={}, count={}", accountId, count);
        }
        return UnreadCountResponseDto.of(count, getCategoryCounts(accountId));
    }

    private Map<NotificationCategory, Long> getCategoryCounts(Long accountId) {
        String categoryKey = CATEGORY_KEY_PREFIX + accountId;
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(categoryKey);

        // 모든 카테고리를 0으로 초기화 — 클라이언트가 누락 키 처리를 하지 않아도 되도록
        Map<NotificationCategory, Long> result = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            result.put(category, 0L);
        }

        if (!cached.isEmpty()) {
            for (Map.Entry<Object, Object> entry : cached.entrySet()) {
                try {
                    result.put(NotificationCategory.valueOf(entry.getKey().toString()),
                            Long.parseLong(entry.getValue().toString()));
                } catch (IllegalArgumentException e) {
                    log.warn("unread 카테고리 캐시 파싱 실패 — 무시: accountId={}, field={}", accountId, entry.getKey());
                }
            }
            return result;
        }

        // 캐시 미스 — DB 집계 후 모든 카테고리(0 포함)를 저장해 "빈 캐시"와 "전부 0"을 구분한다
        result.putAll(notificationRepository.countUnreadByCategory(accountId));
        Map<String, String> toCache = new HashMap<>();
        result.forEach((category, count) -> toCache.put(category.name(), String.valueOf(count)));
        redisTemplate.opsForHash().putAll(categoryKey, toCache);
        return result;
    }

    // ===================== 갱신 (변경 트랜잭션 커밋 후 호출 전제) =====================

    // 알림 생성 — 전체 +1, 카테고리 캐시는 무효화해 다음 조회 시 DB 기준으로 재집계
    public void increment(Long accountId) {
        redisTemplate.opsForValue().increment(UNREAD_KEY_PREFIX + accountId);
        invalidateCategoryCache(accountId);
    }

    // 단건 읽음/삭제 — 전체 -1 (0 미만 방지), 카테고리 캐시 무효화
    public void decrement(Long accountId) {
        redisTemplate.execute(DECR_IF_POSITIVE, List.of(UNREAD_KEY_PREFIX + accountId));
        invalidateCategoryCache(accountId);
    }

    // 전체 읽음/전체 삭제 — 캐시 제거 후 다음 조회 시 DB 기준 복구
    public void clear(Long accountId) {
        redisTemplate.delete(UNREAD_KEY_PREFIX + accountId);
        invalidateCategoryCache(accountId);
    }

    // 일괄 읽음 처리처럼 감소량이 가변적인 경우 DB 기준으로 전체 캐시를 재설정
    public void refreshFromDb(Long accountId) {
        long dbCount = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
        redisTemplate.opsForValue().set(UNREAD_KEY_PREFIX + accountId, String.valueOf(dbCount));
        invalidateCategoryCache(accountId);
    }

    public void invalidateCategoryCache(Long accountId) {
        redisTemplate.delete(CATEGORY_KEY_PREFIX + accountId);
    }
}
