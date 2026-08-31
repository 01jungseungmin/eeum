package com.eeum.eeum.common.service;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

// 주의: 이 락은 lease(TTL) 기반이며 작업이 lease를 초과해도 자동 연장(watchdog)하지 않는다.
// 따라서 재고 차감/결제/예약처럼 정확성이 중요한 쓰기는 이 락만으로 상호배제를 보장하지 않으며,
// 실제 방어는 DB 비관적 락(findByIdWithPessimisticLock)·@Version·Unique 제약이 담당한다.
// 이 락은 "동시 진입 축소(빠른 실패)" 용도로 사용하고, lease는 임계구역 최대 소요시간보다 넉넉히 잡는다.
@Component
@RequiredArgsConstructor
public class RedisLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
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

    public <T> T executeWithLock(
            String key,
            Duration leaseTime,
            Supplier<T> supplier
    ) {
        return executeWithLock(
                key,
                leaseTime,
                ErrorCode.LOCK_ACQUIRE_FAILED,
                supplier
        );
    }

    public <T> T executeWithLock(
            String key,
            Duration leaseTime,
            ErrorCode errorCode,
            Supplier<T> supplier
    ) {
        String lockValue = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, lockValue, leaseTime);

        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(errorCode);
        }

        try {
            return supplier.get();
        } finally {
            releaseLock(key, lockValue);
        }
    }

    public void executeWithLock(
            String key,
            Duration leaseTime,
            Runnable runnable
    ) {
        executeWithLock(
                key,
                leaseTime,
                ErrorCode.LOCK_ACQUIRE_FAILED,
                () -> {
                    runnable.run();
                    return null;
                }
        );
    }

    public void executeWithLock(
            String key,
            Duration leaseTime,
            ErrorCode errorCode,
            Runnable runnable
    ) {
        executeWithLock(
                key,
                leaseTime,
                errorCode,
                () -> {
                    runnable.run();
                    return null;
                }
        );
    }

    private void releaseLock(String key, String lockValue) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(key),
                lockValue
        );
    }
}