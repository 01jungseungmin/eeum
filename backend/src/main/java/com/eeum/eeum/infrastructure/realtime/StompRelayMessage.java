package com.eeum.eeum.infrastructure.realtime;

/**
 * STOMP 중계 메시지.
 *
 * <p>payload를 JSON 문자열로 담는다 — 구체 타입을 실어 나르면 인스턴스마다 배포 버전이
 * 다를 때 역직렬화가 깨진다. 받는 쪽은 Map으로 읽어 브로커로 그대로 넘긴다.
 */
public record StompRelayMessage(String destination, String payloadJson) {
}
