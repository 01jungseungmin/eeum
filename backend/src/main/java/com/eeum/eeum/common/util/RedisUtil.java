package com.eeum.eeum.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    // 값 비교와 삭제를 한 번에 하는 스크립트. GET 후 DEL로 나누면 두 요청이 같은 값을 보고
    // 둘 다 통과한 뒤 하나씩 지워, 일회용이어야 할 토큰이 두 번 쓰인다.
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public void set(String key, String value, long ttlSeconds) { //Redis에 문자열 값을 저장하는 메서드
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Redis TTL은 0보다 커야 합니다.");
        }

        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    //Redis에서 값을 조회하는 메서드
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    //Redis에 해당 key가 존재하는지 확인하는 메서드
    public boolean hasKey(String key) {return Boolean.TRUE.equals(redisTemplate.hasKey(key));}

    //Redis key를 삭제하는 메서드
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 저장된 값이 기대값과 같을 때만 삭제한다 — 일회용 토큰의 원자적 소비용.
     *
     * <p>동시에 같은 토큰으로 들어온 요청 중 정확히 하나만 {@code true}를 받는다.
     * 조회와 삭제를 나눠 쓰면 둘 다 조회를 통과해 민감 작업이 두 번 실행된다.
     *
     * @return 이번 호출이 값을 소비했으면 true, 값이 없거나 다르면 false
     */
    public boolean compareAndDelete(String key, String expectedValue) {
        Long deleted = redisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                List.of(key),
                expectedValue
        );
        return deleted != null && deleted > 0;
    }

    //Redis key의 남은 TTL을 초 단위로 조회하는 메서드
    public long getExpire(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1L;
    }
}