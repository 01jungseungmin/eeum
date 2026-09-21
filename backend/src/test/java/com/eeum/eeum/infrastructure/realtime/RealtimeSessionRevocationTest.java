package com.eeum.eeum.infrastructure.realtime;

import com.eeum.eeum.application.auth.service.AuthAccountReader;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountAuthState;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class RealtimeSessionRevocationTest {
    @Test
    void 지연된_종료신호는_새로운_세대의_연결을_유지한다() {
        WebSocketSessionRegistry sockets = mock(WebSocketSessionRegistry.class);
        SseEmitterManager emitters = mock(SseEmitterManager.class);
        AuthAccountReader reader = mock(AuthAccountReader.class);
        var old = new WebSocketSessionRegistry.ConnectionCredentials(1L, 0L, "old", Long.MAX_VALUE);
        var fresh = new WebSocketSessionRegistry.ConnectionCredentials(1L, 1L, "new", Long.MAX_VALUE);
        var emitter = new SseEmitterManager.ConnectionCredentials(1L, "new");
        when(sockets.connectedCredentials()).thenReturn(Map.of("old", old, "new", fresh));
        when(emitters.connectedCredentials()).thenReturn(Map.of(1L, emitter));
        when(reader.authState(1L)).thenReturn(Optional.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 1L)));
        new RealtimeRelaySubscriber(null, emitters, sockets, null, reader).closeRevokedConnections(1L);
        verify(sockets).closeIfCurrent("old", old, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
        verify(sockets, never()).closeIfCurrent(eq("new"), any(), any());
        verify(emitters, never()).closeIfCurrent(1L, emitter);
    }
}
