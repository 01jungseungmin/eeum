package com.eeum.eeum.infrastructure.realtime;

/**
 * SSE unread 카운트 중계 메시지.
 */
public record UnreadRelayMessage(Long accountId, String payloadJson) {
}
