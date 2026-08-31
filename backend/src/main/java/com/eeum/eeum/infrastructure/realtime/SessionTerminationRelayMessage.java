package com.eeum.eeum.infrastructure.realtime;

/**
 * 해당 계정의 실시간 연결을 끊으라는 신호.
 *
 * <p>세션은 커넥션을 받은 인스턴스의 JVM에만 있는데, 제재는 아무 API 인스턴스에서나 일어난다.
 * Spring 이벤트는 JVM-local이라 그 사이를 넘지 못한다 — 메시지 중계와 같은 이유로 Redis를 거친다.
 */
public record SessionTerminationRelayMessage(Long accountId) {
}
