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
 * <p>{@code @Scheduled}는 JVM마다 독립적으로 돈다. 인스턴스를 2대로 늘리면 모든 스케줄러가
 * 두 대에서 동시에 시작한다. 무중단 배포 중에도 구·신 인스턴스가 잠깐 겹치므로,
 * 평소 1대로 운영하더라도 배포 순간마다 같은 일이 벌어진다.
 *
 * <p>잠금 저장소로 Redis를 쓴다. 토큰·분산락·unread 카운트가 이미 Redis에 의존하고 있어
 * 새로 도입하는 인프라가 없다. 대신 Redis가 비워지면 그 순간 중복 실행이 가능하다 —
 * DB(JDBC provider)가 더 견고하지만 shedlock 테이블을 수동 DDL로 만들어야 해서
 * 마이그레이션 도구가 없는 지금 구성에는 맞지 않는다.
 *
 * <p>{@code defaultLockAtMostFor}는 인스턴스가 잠금을 쥔 채 죽었을 때 잠금이 풀리는 상한이다.
 * 각 스케줄러는 자기 작업 시간에 맞춰 {@code @SchedulerLock}에서 따로 지정한다.
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
