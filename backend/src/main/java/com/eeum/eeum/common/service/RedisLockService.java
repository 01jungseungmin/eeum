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