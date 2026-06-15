package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.ChatRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * 채팅 안 읽음 카운트를 Redis로 관리
 * - 전체:  unread:chat:{accountId}
 * - 방별:  unread:chat:{accountId}:room:{roomId}
 *
 * 알림 도메인의 unread:account:{accountId}(알림함 배지)와는 별개의 채팅 전용 배지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUnreadService {

    // increment(ChatNotificationEventListener)와 resetRoom(ChatUnreadResetEventListener)이
    // 동일 키에 @Async로 경합할 수 있으므로 Lua 스크립트로 read-decrement-delete를 원자적으로 처리
    private static final DefaultRedisScript<Long> RESET_ROOM_SCRIPT = new DefaultRedisScript<>(
            """
            local roomCount = tonumber(redis.call('GET', KEYS[1])) or 0
            if roomCount > 0 then
                redis.call('DECRBY', KEYS[2], roomCount)
                local total = tonumber(redis.call('GET', KEYS[2])) or 0
                if total < 0 then redis.call('SET', KEYS[2], '0') end
            end
            redis.call('DEL', KEYS[1])
            return roomCount
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    private String accountKey(Long accountId) {
        return ChatRedisKeys.totalUnread(accountId);
    }

    private String roomKey(Long accountId, Long roomId) {
        return ChatRedisKeys.roomUnread(accountId, roomId);
    }

    // 메시지 수신 시 전체/방별 카운트 증가 (Redis 전용 — DB 트랜잭션 불필요)
    public void increment(Long accountId, Long roomId) {
        redisTemplate.opsForValue().increment(accountKey(accountId));
        redisTemplate.opsForValue().increment(roomKey(accountId, roomId));
    }

    // 방을 읽음 처리 — Lua 스크립트로 방별 카운트 읽기 + 전체 차감 + 방 키 삭제를 원자적으로 수행
    public void resetRoom(Long accountId, Long roomId) {
        redisTemplate.execute(
                RESET_ROOM_SCRIPT,
                List.of(roomKey(accountId, roomId), accountKey(accountId))
        );
    }

    // 전체 안 읽은 채팅 수 — 캐시 미스 시 DB fallback 후 복구 (Redis 전용 — DB 트랜잭션 불필요)
    public long getTotalUnread(Long accountId, LongSupplier dbFallback) {
        String key = accountKey(accountId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return parse(cached);
        }
        long count = dbFallback.getAsLong();
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(count));
        log.debug("채팅 unread 캐시 복구: accountId={}, count={}", accountId, count);
        return count;
    }

    // 방별 안 읽은 수 조회 — 캐시 미스 시 DB fallback 후 복구
    public long getRoomUnread(Long accountId, Long roomId, LongSupplier dbFallback) {
        String key = roomKey(accountId, roomId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return parse(cached);
        }
        long count = dbFallback.getAsLong();
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(count));
        return count;
    }

    private long parse(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
