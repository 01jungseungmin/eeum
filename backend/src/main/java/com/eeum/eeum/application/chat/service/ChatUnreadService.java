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

    // KEYS[1]=방별 키, KEYS[2]=전체 키 — 두 카운트를 원자적으로 증가
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('INCR', KEYS[1])
            return redis.call('INCR', KEYS[2])
            """,
            Long.class
    );

    // KEYS[1]=전체 키, ARGV[1]=기대값(''이면 키 없음), ARGV[2]=새 값
    // 기대값과 일치할 때만 SET — 그 사이 increment가 있었으면 덮어쓰지 않는다
    private static final DefaultRedisScript<Long> COMPARE_AND_SET_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if current == false then current = '' end
            if current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2])
                return 1
            end
            return 0
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
    // 두 키를 개별 INCR하면 그 사이에 resetRoom이 끼어들 때 전체 카운트만 남아 배지가 영구히 부풀어 오른다:
    //   INCR room(=1) → reset(room 1 읽고 total 차감 후 room DEL) → INCR total(=1)
    //   결과: room 키는 없는데 total=1 → 리셋할 방이 없어 회수 불가
    // 따라서 두 키 증가를 하나의 Lua로 원자 처리한다.
    public void increment(Long accountId, Long roomId) {
        redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(roomKey(accountId, roomId), accountKey(accountId))
        );
    }

    // 방을 읽음 처리 — Lua 스크립트로 방별 카운트 읽기 + 전체 차감 + 방 키 삭제를 원자적으로 수행
    public void resetRoom(Long accountId, Long roomId) {
        redisTemplate.execute(
                RESET_ROOM_SCRIPT,
                List.of(roomKey(accountId, roomId), accountKey(accountId))
        );
    }

    // 정합성 보정용 CAS — 읽은 값이 그대로일 때만 덮어쓴다.
    // 무조건 SET하면 "DB 조회 → SET" 사이에 들어온 increment가 통째로 사라진다.
    // 값이 바뀌었으면(=그 사이 메시지가 왔으면) 건너뛰고 다음 주기에 다시 맞춘다.
    // expected가 null이면 "키가 없어야 함"을 의미한다.
    public boolean compareAndSetTotal(Long accountId, Long expected, long newValue) {
        Long result = redisTemplate.execute(
                COMPARE_AND_SET_SCRIPT,
                List.of(accountKey(accountId)),
                expected == null ? "" : String.valueOf(expected),
                String.valueOf(newValue)
        );
        return result != null && result == 1L;
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
