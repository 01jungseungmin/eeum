package com.eeum.eeum.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 전달을 Redis로 중계한다 — <b>모든 인스턴스</b>에서 발행한다.
 *
 * <p>인메모리 STOMP 브로커와 SseEmitter는 커넥션을 받은 JVM 안에만 존재한다.
 * 반면 이벤트는 그 요청을 처리한 아무 인스턴스에서나 발생한다. 두 곳이 다르면 전달이 사라진다.
 * 그래서 발행은 Redis 채널로 하고, 실시간 커넥션을 가진 인스턴스가 구독해서 밀어낸다.
 *
 * <p>실시간 인스턴스에서도 자기가 발행한 것을 다시 받아 처리한다. 경로를 하나로 두면
 * "어디서 발생했는가"를 따질 필요가 없다.
 *
 * <p>전달 실패는 삼킨다 — 실시간 push는 부가 기능이고, 이미 커밋된 본 작업을 되돌릴 이유가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRelayPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishStomp(String destination, Object payload) {
        publish(RealtimeRelayChannels.STOMP, new StompRelayMessage(destination, toJson(payload)));
    }

    public void publishUnreadCount(Long accountId, Object unreadCount) {
        publish(RealtimeRelayChannels.SSE_UNREAD,
                new UnreadRelayMessage(accountId, toJson(unreadCount)));
    }

    private void publish(String channel, Object message) {
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("실시간 중계 발행 실패 — channel={}", channel, e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("실시간 중계 payload 직렬화 실패", e);
        }
    }
}
