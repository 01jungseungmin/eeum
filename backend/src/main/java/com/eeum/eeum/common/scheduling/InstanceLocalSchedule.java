package com.eeum.eeum.common.scheduling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 스케줄은 인스턴스마다 독립적으로 돌아야 한다는 표시.
 *
 * 기본 규칙은 모든 @Scheduled에 분산 잠금을 거는 것이다(SchedulerRuleTest).
 * 그런데 WebSocket 세션·SSE emitter처럼 JVM 안에만 있는 자원은 정반대다 — 잠금으로 한 대만
 * 돌게 하면 나머지 인스턴스의 자원이 영영 정리되지 않는다. 이중 처리가 아니라 미처리가 문제다.
 * 공유 데이터(DB 행, Redis 키)를 건드리는 스케줄에는 절대 붙이지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InstanceLocalSchedule {
}
