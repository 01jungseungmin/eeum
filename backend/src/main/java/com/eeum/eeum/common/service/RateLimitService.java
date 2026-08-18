package com.eeum.eeum.common.service;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    // 쿨다운형 — SET NX + TTL. 같은 키로 cooldown 동안 1회만 허용 (이메일 인증 발송, 비밀번호 재설정 메일 등)
    // Redis 전용 메서드 — DB 트랜잭션과 무관
    public void checkCooldown(String key, Duration cooldown, ErrorCode errorCode) {
        Boolean firstRequest = redisTemplate.opsForValue().setIfAbsent(key, "1", cooldown);

        if (!Boolean.TRUE.equals(firstRequest)) {
            throw new BusinessException(errorCode);
        }
    }

    // 쿨다운형(비예외) — 획득에 성공하면 true, 이미 쿨다운 중이면 false.
    // checkCooldown과 달리 예외를 던지지 않는다. 사용자 요청을 막는 것이 아니라
    // "막히면 조용히 건너뛰는" 내부 경로(운영 실패 알림 스로틀 등)에서 쓴다.
    public boolean tryAcquireCooldown(String key, Duration cooldown) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", cooldown));
    }

    // 카운터형 — window 동안 누적된 실패 횟수가 maxAttempts 이상이면 차단 (로그인 실패 등)
    public void checkNotBlocked(String key, int maxAttempts, ErrorCode errorCode) {
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return;
        }

        int failureCount;

        try {
            failureCount = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            redisTemplate.delete(key);
            return;
        }

        if (failureCount >= maxAttempts) {
            throw new BusinessException(errorCode);
        }
    }

    // 카운터형 — window 동안 누적 호출 횟수가 maxRequests를 초과하면 차단 (공개 API 스팸/과호출 방지 등).
    // checkNotBlocked와 달리 이 메서드 자체가 호출마다 카운트를 증가시킨다(선-검증 후 별도 기록이 필요 없음).
    public void checkAndIncrement(String key, int maxRequests, Duration window, ErrorCode errorCode) {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }

        if (count != null && count > maxRequests) {
            throw new BusinessException(errorCode);
        }
    }

    // 실패 1회 기록 — 최초 실패(count == 1) 시에만 TTL 설정
    public void recordFailure(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
    }

    // 성공 시 실패 카운트 초기화
    public void resetFailure(String key) {
        redisTemplate.delete(key);
    }
}
