package com.eeum.eeum.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

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

    //Redis key의 남은 TTL을 초 단위로 조회하는 메서드
    public long getExpire(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1L;
    }
}