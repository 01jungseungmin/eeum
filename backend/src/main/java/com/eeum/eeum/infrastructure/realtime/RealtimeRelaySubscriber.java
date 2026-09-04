package com.eeum.eeum.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

// Redis 중계 메시지를 이 인스턴스의 STOMP/SSE 연결로 전달한다.
@Slf4j
@Component
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RealtimeRelaySubscriber {

    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final SseEmitterManager sseEmitterManager;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @org.springframework.context.annotation.Bean
    public RedisMessageListenerContainer realtimeRelayListenerContainer(
            RedisConnectionFactory connectionFactory,
            @Qualifier("realtimeRelayDispatchExecutor") ThreadPoolTaskExecutor realtimeRelayDispatchExecutor,
            @Qualifier("realtimeRelaySubscriptionExecutor") ThreadPoolTaskExecutor realtimeRelaySubscriptionExecutor) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 기본값은 SimpleAsyncTaskExecutor다 — 수신 메시지 하나마다 스레드를 새로 만든다.
        // 실시간 이벤트는 알림이 몰릴 때 같이 몰리므로 그 지점에서 스레드가 무제한으로 늘어난다.
        container.setTaskExecutor(realtimeRelayDispatchExecutor);
        // 구독 태스크는 Redis 커넥션에 붙어 계속 살아 있는 장기 작업이라 위 풀에 태우면
        // 스레드 하나를 영구 점유한다. 별도 executor로 분리한다.
        container.setSubscriptionExecutor(realtimeRelaySubscriptionExecutor);
        container.addMessageListener(stompListener(), new ChannelTopic(RealtimeRelayChannels.STOMP));
        container.addMessageListener(unreadListener(), new ChannelTopic(RealtimeRelayChannels.SSE_UNREAD));
        return container;
    }

    @org.springframework.context.annotation.Bean
    public RedisMessageListenerContainer sessionTerminationRelayListenerContainer(
            RedisConnectionFactory connectionFactory,
            @Qualifier("sessionTerminationRelayExecutor") ThreadPoolTaskExecutor sessionTerminationRelayExecutor,
            @Qualifier("realtimeRelaySubscriptionExecutor") ThreadPoolTaskExecutor realtimeRelaySubscriptionExecutor) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(sessionTerminationRelayExecutor);
        container.setSubscriptionExecutor(realtimeRelaySubscriptionExecutor);
        container.addMessageListener(
                sessionTerminationListener(),
                new ChannelTopic(RealtimeRelayChannels.SESSION_TERMINATION));
        return container;
    }

    // 수신 처리는 짧다 — SSE 전송은 SseEmitterManager가 자기 풀로 넘기고,
    // STOMP는 인메모리 브로커로 넘긴다. 그래서 작은 고정 풀로 충분하다.
    @org.springframework.context.annotation.Bean
    public ThreadPoolTaskExecutor realtimeRelayDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("relay-");
        executor.setRejectedExecutionHandler((task, poolExecutor) ->
                log.warn("실시간 중계 큐 포화 — 수신 처리를 건너뛴다. active={}, queued={}",
                        poolExecutor.getActiveCount(), poolExecutor.getQueue().size()));
        executor.initialize();
        return executor;
    }

    @org.springframework.context.annotation.Bean
    public ThreadPoolTaskExecutor realtimeRelaySubscriptionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("relay-subscribe-");
        executor.initialize();
        return executor;
    }

    @org.springframework.context.annotation.Bean
    public ThreadPoolTaskExecutor sessionTerminationRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("relay-session-termination-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    private MessageListener stompListener() {
        return (Message message, byte[] pattern) -> handle(message, body -> {
            StompRelayMessage relayed = objectMapper.readValue(body, StompRelayMessage.class);
            // 구체 타입 대신 Map으로 읽어 브로커에 넘긴다 — 컨버터가 같은 JSON으로 다시 직렬화한다.
            Object payload = objectMapper.readValue(relayed.payloadJson(), Map.class);
            messagingTemplateProvider.getObject().convertAndSend(relayed.destination(), payload);
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
            sseEmitterManager.closeAll(relayed.accountId());
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
