package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class AccountSessionTerminationListenerTest {

    @InjectMocks AccountSessionTerminationListener listener;
    @Mock RealtimeRelayPublisher relayPublisher;

    @Test
    void 제재로_토큰을_회수하면_연결_종료_신호를_중계한다() {
        // Given: 세션은 실시간 인스턴스에만 있고 제재는 아무 API 인스턴스에서나 일어난다.
        //        Spring 이벤트는 JVM-local이라 직접 끊을 수 없다 — Redis로 넘겨야 한다.

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(1L));

        // Then
        verify(relayPublisher).publishSessionTermination(1L);
    }

    @Test
    void refresh_토큰만_회수하는_경우에도_연결을_끊는다() {
        // Given: 본인 탈퇴·비밀번호 재설정·사장 승격 모두 "다시 인증받게 한다"는 뜻이다

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.refreshOnly(2L));

        // Then
        verify(relayPublisher).publishSessionTermination(2L);
    }

    @Test
    void 계정과_무관한_정리에는_연결을_건드리지_않는다() {
        // Given: OAuth 임시 토큰 정리는 특정 계정의 세션과 관계가 없다

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.oauthTemp("temp-token"));

        // Then
        verify(relayPublisher, never()).publishSessionTermination(anyLong());
    }

    @Test
    void 세션_종료_실패가_예외로_전파되지_않는다() {
        // Given: DB는 이미 커밋됐다. 소켓 정리 실패가 원 작업을 되돌리게 하면 안 된다.
        doThrow(new IllegalStateException("redis down"))
                .when(relayPublisher).publishSessionTermination(anyLong());

        // When & Then
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(3L));
    }
}
