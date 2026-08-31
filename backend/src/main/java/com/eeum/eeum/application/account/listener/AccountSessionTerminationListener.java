package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refresh Token을 회수하는 경우 열린 WebSocket 연결도 함께 끊는다.
 *
 * <p>인바운드 프레임에는 계정 상태 검사가 있지만 <b>수신은 인바운드가 아니다.</b>
 * 이미 구독을 걸어둔 연결은 상대가 보낸 메시지·읽음·타이핑을 계속 받으므로,
 * 정지·탈퇴시켜도 Access Token 수명(30분) 동안 대화를 계속 들여다볼 수 있었다.
 *
 * <p>기준을 "제재"가 아니라 <b>"Refresh Token 회수"</b>로 잡았다. 그 회수는 곧
 * "이 세션을 무효화하고 다시 인증받게 한다"는 뜻이고, 그렇다면 살아 있는 소켓도 끊는 것이 일관된다.
 * 제재·강제 탈퇴뿐 아니라 본인 탈퇴, 비밀번호 재설정, 사장 승격(재로그인 유도)이 모두 여기 해당한다.
 *
 * <p>브로커가 꺼진 인스턴스에는 끊을 세션이 없다 — {@code WebSocketConfig}와 같은 조건으로 묶는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class AccountSessionTerminationListener {

    private final WebSocketSessionRegistry sessionRegistry;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountTokenCleanup(AccountTokenCleanupEvent event) {
        if (event.accountId() == null || !event.deleteRefreshToken()) {
            return;
        }

        try {
            int closed = sessionRegistry.closeAll(
                    event.accountId(), WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
            if (closed > 0) {
                log.info("계정 상태 변경으로 WebSocket 연결 종료: accountId={}, 세션={}개",
                        event.accountId(), closed);
            }
        } catch (Exception e) {
            // DB는 이미 커밋됐다. 소켓 정리 실패가 원 작업을 되돌리게 하지 않는다.
            log.error("WebSocket 연결 종료 실패: accountId={}", event.accountId(), e);
        }
    }
}
