package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.ChatRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final StringRedisTemplate redisTemplate;

    private String accountKey(Long accountId) {
        return ChatRedisKeys.totalUnread(accountId);
    }

    private String roomKey(Long accountId, Long roomId) {
        return ChatRedisKeys.roomUnread(accountId, roomId);
    }

    // 메시지 수신 시 전체/방별 카운트 증가
    @Transactional
    public void increment(Long accountId, Long roomId) {
        redisTemplate.opsForValue().increment(accountKey(accountId));
        redisTemplate.opsForValue().increment(roomKey(accountId, roomId));
    }

    // 방을 읽음 처리 — 방별 카운트만큼 전체에서 차감 후 방 키 삭제
    @Transactional
    public void resetRoom(Long accountId, Long roomId) {
        String roomKey = roomKey(accountId, roomId);
        long roomUnread = parse(redisTemplate.opsForValue().get(roomKey));
        if (roomUnread > 0) {
            decrementAccountBy(accountId, roomUnread);
        }
        redisTemplate.delete(roomKey);
    }

    // 전체 안 읽은 채팅 수 — 캐시 미스 시 DB fallback 후 복구
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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

    private void decrementAccountBy(Long accountId, long amount) {
        String key = accountKey(accountId);
        Long result = redisTemplate.opsForValue().increment(key, -amount);
        if (result != null && result < 0) {
            redisTemplate.opsForValue().set(key, "0");
        }
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
