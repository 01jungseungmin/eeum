package com.eeum.eeum.config;

import com.eeum.eeum.security.websocket.StompAuthChannelInterceptor;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 브로커 설정 — 실시간 트래픽을 받는 인스턴스에서만 켠다.
 *
 * <p>인메모리 브로커라 구독 정보가 JVM 안에만 있다. 인스턴스를 늘리면 A 서버에서 발행한
 * 메시지가 B 서버에 붙은 구독자에게 가지 않는다. 그래서 WebSocket 커넥션을 한 인스턴스로
 * 모으고(LB 경로 라우팅), API 전용 인스턴스에서는 이 설정을 끈다.
 *
 * <p>{@code eeum.realtime.enabled=false}로 끈다. 기본값은 켬이라 단일 인스턴스·개발·테스트
 * 환경의 동작은 그대로다.
 *
 * <p><b>주의:</b> 이 설정을 끄면 {@code SimpMessagingTemplate} 빈도 사라진다.
 * 그 빈에 의존하는 채팅 브로드캐스트 경로도 같은 조건으로 묶여 있다.
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:8081",
                        "http://localhost:5173",
                        "http://localhost:63342"
                );
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트 → 서버 메시지 경로 접두사
        registry.setApplicationDestinationPrefixes("/pub");
        // 서버 → 클라이언트 구독 경로 접두사 (인메모리 브로커)
        registry.enableSimpleBroker("/sub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /**
     * 세션을 레지스트리에 등록·해제한다 — 제재·탈퇴 시 열린 연결을 끊기 위해서다.
     *
     * <p>여기는 HTTP 업그레이드 시점이라 아직 STOMP CONNECT 전이고, 누구의 연결인지 모른다.
     * 계정 바인딩은 인증을 끝낸 뒤 {@code StompAuthChannelInterceptor}가 붙인다.
     *
     * <p>해제를 여기서 하는 이유는 정상 종료(DISCONNECT 프레임)든 비정상 종료(네트워크 단절)든
     * 이 콜백은 반드시 지나기 때문이다. DISCONNECT 프레임만 보면 끊긴 세션이 쌓인다.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRegistry.register(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
                    throws Exception {
                sessionRegistry.unregister(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }
}
