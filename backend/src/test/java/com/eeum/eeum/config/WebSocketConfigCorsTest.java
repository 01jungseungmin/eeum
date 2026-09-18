package com.eeum.eeum.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 허용 출처가 REST(SecurityConfig)와 웹소켓(WebSocketConfig)에 따로 적혀 있어서,
// 배포 도메인을 REST에만 추가하고 웹소켓을 빠뜨린 적이 있다. API는 전부 도는데
// 채팅만 연결이 끊겨 원인을 찾기 어려웠다. 두 곳이 같은 목록을 쓰는지 고정한다.
class WebSocketConfigCorsTest {

    private static final List<String> CONFIGURED_ORIGINS = List.of(
            "http://localhost:3000",
            "https://eeum.life",
            "https://*.vercel.app"
    );

    private String[] registeredOrigins() {
        WebSocketConfig webSocketConfig = new WebSocketConfig(null, null);
        ReflectionTestUtils.setField(webSocketConfig, "allowedOrigins", CONFIGURED_ORIGINS);

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint(anyString())).thenReturn(registration);

        webSocketConfig.registerStompEndpoints(registry);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(registration).setAllowedOriginPatterns(captor.capture());
        return captor.getValue();
    }

    @Test
    void 웹소켓은_REST와_같은_허용_출처_목록을_쓴다() {
        assertThat(registeredOrigins()).containsExactlyElementsOf(CONFIGURED_ORIGINS);
    }

    @Test
    void 배포_도메인_와일드카드가_웹소켓에도_반영된다() {
        // 이 항목이 빠지면 API는 되는데 채팅만 조용히 끊긴다.
        assertThat(registeredOrigins()).contains("https://*.vercel.app");
    }
}
