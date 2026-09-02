package com.eeum.eeum.common.scheduling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 스케줄은 <b>인스턴스마다 독립적으로 돌아야 한다</b>는 표시.
 *
 * <p>기본 규칙은 모든 {@code @Scheduled}에 분산 잠금을 거는 것이다({@code SchedulerRuleTest}).
 * 공유 데이터를 처리하는 스케줄러가 여러 인스턴스에서 동시에 돌면 이중 처리되기 때문이다.
 *
 * <p>그런데 <b>JVM 안에만 있는 자원</b>을 다루는 스케줄은 정반대다. WebSocket 세션이나
 * SSE emitter는 그 커넥션을 받은 인스턴스에만 존재하므로, 잠금을 걸어 한 대만 돌게 하면
 * 나머지 인스턴스의 자원은 영영 정리되지 않는다. 이중 처리가 아니라 <b>미처리</b>가 문제가 된다.
 *
 * <p>붙일 때는 왜 인스턴스마다 돌아야 하는지를 해당 스케줄러 주석에 남긴다.
 * 공유 데이터(DB 행, Redis 키)를 건드리는 스케줄에는 절대 붙이지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InstanceLocalSchedule {
}
