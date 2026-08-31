package com.eeum.eeum.infrastructure.realtime;

/**
 * 실시간 중계 Redis 채널.
 * 기존 Redis 키 패턴(refresh·blacklist·unread·rate-limit·shedlock)과 겹치지 않는 접두사를 쓴다.
 */
public final class RealtimeRelayChannels {

    public static final String STOMP = "realtime:stomp";
    public static final String SSE_UNREAD = "realtime:sse-unread";

    private RealtimeRelayChannels() {
    }
}
