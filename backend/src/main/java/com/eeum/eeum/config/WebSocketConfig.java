package com.eeum.eeum.config;

import com.eeum.eeum.security.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
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
}
