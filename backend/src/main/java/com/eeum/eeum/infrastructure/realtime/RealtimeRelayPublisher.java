package com.eeum.eeum.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 실시간 전달을 Redis로 중계한다 — 모든 인스턴스에서 발행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRelayPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishStomp(String destination, Object payload) {
        publish(RealtimeRelayChannels.STOMP,
                () -> new StompRelayMessage(destination, objectMapper.writeValueAsString(payload)));
    }

    public void publishUnreadCount(Long accountId, Object unreadCount) {
        publish(RealtimeRelayChannels.SSE_UNREAD,
                () -> new UnreadRelayMessage(accountId, objectMapper.writeValueAsString(unreadCount)));
    }

    /**
     * 계정의 실시간 연결 종료 신호.
     *
     * 다른 중계와 달리 부가 기능이 아니다. 전달되지 않으면 제재된 계정의 구독이
     * 계속 살아 있다. 다만 여기서 예외를 던져 원 작업(정지·탈퇴)을 되돌릴 수는 없으므로,
     * 최종 보장은 Redis Pub/Sub이 아니라 계정에 남긴 무효화 시각이 맡는다.
     */
    public void publishSessionTermination(Long accountId) {
        publish(RealtimeRelayChannels.SESSION_TERMINATION,
                () -> new SessionTerminationRelayMessage(accountId));
    }

    // payload 직렬화까지 이 경계 안에서 한다.
    // 호출부는 대부분 @TransactionalEventListener(AFTER_COMMIT)이라, 여기서 예외가 새어 나가면
    // 이미 커밋이 끝난 요청이 실패로 응답된다 — 실시간 push 하나 때문에 주문·메시지가 실패하면 안 된다.
    private void publish(String channel, RelayMessageSupplier messageSupplier) {
        try {
            redisTemplate.convertAndSend(
                    channel, objectMapper.writeValueAsString(messageSupplier.get()));
        } catch (Exception e) {
            log.warn("실시간 중계 발행 실패 — channel={}", channel, e);
        }
    }

    @FunctionalInterface
    private interface RelayMessageSupplier {
        Object get() throws JsonProcessingException;
    }
}
