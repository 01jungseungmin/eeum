package com.eeum.eeum.common.service;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @InjectMocks
    private RateLimitService rateLimitService;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private static final String KEY = "rate-limit:ai-exposure-view:ip:127.0.0.1";

    @Test
    void 첫_호출이면_TTL을_설정하고_통과한다() {
        // given
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), anyString()))
                .thenReturn(1L);

        // when
        rateLimitService.checkAndIncrement(KEY, 60, Duration.ofMinutes(1), ErrorCode.AI_RATE_LIMITED);

        // then
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), eq("60000"));
    }

    @Test
    void 한도_이내면_TTL을_다시_설정하지_않고_통과한다() {
        // given
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), anyString()))
                .thenReturn(30L);

        // when
        rateLimitService.checkAndIncrement(KEY, 60, Duration.ofMinutes(1), ErrorCode.AI_RATE_LIMITED);

        // then
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), eq("60000"));
    }

    @Test
    void 한도를_초과하면_AI_RATE_LIMITED_예외가_발생한다() {
        // given
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), anyString()))
                .thenReturn(61L);

        // when & then
        assertThatThrownBy(() -> rateLimitService.checkAndIncrement(KEY, 60, Duration.ofMinutes(1), ErrorCode.AI_RATE_LIMITED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_RATE_LIMITED);
    }

    @Test
    void 정확히_한도에_도달하면_아직_차단하지_않는다() {
        // given
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), anyString()))
                .thenReturn(60L);

        // when & then
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                        rateLimitService.checkAndIncrement(KEY, 60, Duration.ofMinutes(1), ErrorCode.AI_RATE_LIMITED)))
                .isNull();
    }

    @Test
    void 로그인_실패_증가와_TTL_설정은_하나의_Redis_명령으로_처리한다() {
        // Given: INCR과 EXPIRE가 나뉘면 첫 명령 뒤 장애로 만료 없는 차단 키가 남는다.
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), anyString()))
                .thenReturn(1L);

        // When
        rateLimitService.recordFailure(KEY, Duration.ofMinutes(5));

        // Then
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class), eq(java.util.List.of(KEY)), eq("300000"));
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }
}
