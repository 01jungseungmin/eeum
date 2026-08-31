package com.eeum.eeum.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Redis로 중계된 실시간 메시지를 받아 이 인스턴스의 커넥션으로 밀어낸다.
 *
 * <p>실시간 커넥션을 가진 인스턴스에서만 뜬다 — API 전용 인스턴스에는 브로커도 emitter도 없어
 * 구독할 이유가 없다. 발행({@link RealtimeRelayPublisher})은 모든 인스턴스에서 한다.
 *
 * <p>수신 처리에서 예외가 나도 삼킨다. 한 건의 실패가 리스너 컨테이너를 멈추면
 * 이후 모든 실시간 전달이 끊긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RealtimeRelaySubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final SseEmitterManager sseEmitterManager;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @org.springframework.context.annotation.Bean
    public RedisMessageListenerContainer realtimeRelayListenerContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(stompListener(), new ChannelTopic(RealtimeRelayChannels.STOMP));
        container.addMessageListener(unreadListener(), new ChannelTopic(RealtimeRelayChannels.SSE_UNREAD));
        container.addMessageListener(
                sessionTerminationListener(),
                new ChannelTopic(RealtimeRelayChannels.SESSION_TERMINATION));
        return container;
    }

    private MessageListener stompListener() {
        return (Message message, byte[] pattern) -> handle(message, body -> {
            StompRelayMessage relayed = objectMapper.readValue(body, StompRelayMessage.class);
            // 구체 타입 대신 Map으로 읽어 브로커에 넘긴다 — 컨버터가 같은 JSON으로 다시 직렬화한다.
            Object payload = objectMapper.readValue(relayed.payloadJson(), Map.class);
            messagingTemplate.convertAndSend(relayed.destination(), payload);
        });
    }

    private MessageListener unreadListener() {
        return (Message message, byte[] pattern) -> handle(message, body -> {
            UnreadRelayMessage relayed = objectMapper.readValue(body, UnreadRelayMessage.class);
            if (!sseEmitterManager.isConnected(relayed.accountId())) {
                return;   // 이 인스턴스에 붙어 있지 않은 계정
            }
            Object payload = objectMapper.readValue(relayed.payloadJson(), Map.class);
            sseEmitterManager.sendUnreadCount(relayed.accountId(), payload);
        });
    }

    // 이 인스턴스에 붙어 있지 않은 계정이면 closeAll이 0을 돌려주고 끝난다.
    private MessageListener sessionTerminationListener() {
        return (Message message, byte[] pattern) -> handle(message, body -> {
            SessionTerminationRelayMessage relayed =
                    objectMapper.readValue(body, SessionTerminationRelayMessage.class);
            int closed = sessionRegistry.closeAll(
                    relayed.accountId(), WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
            if (closed > 0) {
                log.info("계정 상태 변경으로 WebSocket 연결 종료: accountId={}, 세션={}개",
                        relayed.accountId(), closed);
            }
        });
    }

    private void handle(Message message, RelayHandler handler) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            handler.handle(body);
        } catch (Exception e) {
            log.warn("실시간 중계 수신 처리 실패 — body={}", body, e);
        }
    }

    @FunctionalInterface
    private interface RelayHandler {
        void handle(String body) throws Exception;
    }
}
