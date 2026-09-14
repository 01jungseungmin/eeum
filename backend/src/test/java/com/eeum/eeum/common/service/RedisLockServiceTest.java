package com.eeum.eeum.common.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 락 임계구역이 끝난 뒤 Redis 해제만 실패해도 이미 확정된 작업 결과는 보존해야 한다.
 *
 * 결함 위치: RedisLockService.java의 finally 해제 경로. 해제 예외가 supplier 결과나
 * 원래 예외를 덮으면 DB에는 반영됐지만 요청은 실패한 것으로 보인다.
 */
@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @Test
    void 락_해제만_실패하면_완료된_작업_결과를_반환한다() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(), any(), any())).thenThrow(new RuntimeException("redis unavailable"));
        RedisLockService service = new RedisLockService(redisTemplate);

        // When
        String result = service.executeWithLock("lock:test", Duration.ofSeconds(5), () -> "committed");

        // Then
        assertThat(result).isEqualTo("committed");
    }

    @Test
    void 작업과_락_해제가_모두_실패하면_작업_예외를_보존한다() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(), any(), any())).thenThrow(new RuntimeException("release failed"));
        RedisLockService service = new RedisLockService(redisTemplate);
        IllegalStateException operationFailure = new IllegalStateException("operation failed");

        // When & Then
        assertThatThrownBy(() -> service.executeWithLock(
                "lock:test", Duration.ofSeconds(5), () -> { throw operationFailure; }))
                .isSameAs(operationFailure);
    }
}
