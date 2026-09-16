package com.eeum.eeum.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 스케줄러 중복 실행 방지.
 *
 * Spring의 @Scheduled는 JVM마다 독립적으로 돈다. 1대로 운영해도 무중단 배포 중에는
 * 구·신 인스턴스가 겹치므로 배포 순간마다 같은 일이 벌어진다.
 * 저장소로 Redis를 쓴 건 이미 의존하고 있어 새 인프라가 없어서다 — 대신 Redis가 비면
 * 그 순간 중복이 가능하다. defaultLockAtMostFor는 잠금을 쥔 채 죽었을 때의 해제 상한이다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    private static final String LOCK_KEY_PREFIX = "shedlock";

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
        return new RedisLockProvider(redisConnectionFactory, LOCK_KEY_PREFIX);
    }
}
