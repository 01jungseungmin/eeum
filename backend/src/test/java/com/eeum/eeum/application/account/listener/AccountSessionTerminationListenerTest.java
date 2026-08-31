package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSessionTerminationListenerTest {

    @InjectMocks AccountSessionTerminationListener listener;
    @Mock WebSocketSessionRegistry sessionRegistry;

    @Test
    void 제재로_토큰을_회수하면_열린_연결도_끊는다() {
        // Given: 인바운드 검사만으로는 이미 걸어둔 구독의 수신을 막지 못한다
        when(sessionRegistry.closeAll(anyLong(), any())).thenReturn(1);

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(1L));

        // Then
        verify(sessionRegistry).closeAll(1L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void refresh_토큰만_회수하는_경우에도_연결을_끊는다() {
        // Given: 본인 탈퇴·비밀번호 재설정·사장 승격 모두 "다시 인증받게 한다"는 뜻이다
        when(sessionRegistry.closeAll(anyLong(), any())).thenReturn(0);

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.refreshOnly(2L));

        // Then
        verify(sessionRegistry).closeAll(2L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void 계정과_무관한_정리에는_연결을_건드리지_않는다() {
        // Given: OAuth 임시 토큰 정리는 특정 계정의 세션과 관계가 없다

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.oauthTemp("temp-token"));

        // Then
        verify(sessionRegistry, never()).closeAll(anyLong(), any());
    }

    @Test
    void 세션_종료_실패가_예외로_전파되지_않는다() {
        // Given: DB는 이미 커밋됐다. 소켓 정리 실패가 원 작업을 되돌리게 하면 안 된다.
        when(sessionRegistry.closeAll(anyLong(), any()))
                .thenThrow(new IllegalStateException("registry down"));

        // When & Then
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(3L));
    }
}
