package com.eeum.eeum.security.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 계정별 세션 레지스트리.
 *
 * <p>여기서 막는 것은 두 가지다 — 제재된 계정의 연결이 살아남는 것,
 * 그리고 끊긴 세션 ID가 계정 인덱스에 쌓여 누수가 되는 것.
 */
class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    @Test
    void 계정의_모든_세션을_끊는다() throws Exception {
        // Given: 한 계정이 여러 기기로 붙어 있다
        WebSocketSession phone = session("s1");
        WebSocketSession tablet = session("s2");
        registry.register(phone);
        registry.register(tablet);
        registry.bindAccount("s1", 1L, 0L);
        registry.bindAccount("s2", 1L, 0L);

        // When
        int closed = registry.closeAll(1L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);

        // Then
        assertThat(closed).isEqualTo(2);
        verify(phone).close(WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
        verify(tablet).close(WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
        assertThat(registry.sessionCount(1L)).isZero();
    }

    @Test
    void 다른_계정의_세션은_건드리지_않는다() throws Exception {
        // Given
        WebSocketSession mine = session("s1");
        WebSocketSession other = session("s2");
        registry.register(mine);
        registry.register(other);
        registry.bindAccount("s1", 1L, 0L);
        registry.bindAccount("s2", 2L, 0L);

        // When
        registry.closeAll(1L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);

        // Then
        verify(mine).close(any());
        verify(other, never()).close(any());
        assertThat(registry.sessionCount(2L)).isEqualTo(1);
    }

    @Test
    void 연결이_끊기면_계정_인덱스에서도_빠진다() {
        // Given: 세션만 지우고 계정 인덱스를 두면 끊긴 ID가 계속 쌓인다
        registry.register(session("s1"));
        registry.bindAccount("s1", 1L, 0L);

        // When
        registry.unregister("s1");

        // Then
        assertThat(registry.sessionCount(1L)).isZero();
    }

    @Test
    void 한_세션이_끊기지_않아도_나머지는_끊는다() throws Exception {
        // Given: 이미 죽은 소켓이 섞여 있어도 정리가 멈추면 안 된다
        WebSocketSession broken = session("s1");
        WebSocketSession alive = session("s2");
        doThrow(new IOException("already closed")).when(broken).close(any());
        registry.register(broken);
        registry.register(alive);
        registry.bindAccount("s1", 1L, 0L);
        registry.bindAccount("s2", 1L, 0L);

        // When
        int closed = registry.closeAll(1L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);

        // Then
        assertThat(closed).isEqualTo(1);
        verify(alive).close(any());
    }

    @Test
    void 세션이_없는_계정은_조용히_넘어간다() {
        // When & Then
        assertThat(registry.closeAll(999L, CloseStatus.NORMAL)).isZero();
    }
}
