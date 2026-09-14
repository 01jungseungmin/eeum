package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Refresh Token 회수 이벤트를 모든 인스턴스에 중계해 열린 WebSocket 연결도 종료한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSessionTerminationListener {

    private final RealtimeRelayPublisher relayPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountTokenCleanup(AccountTokenCleanupEvent event) {
        if (event.accountId() == null || !event.deleteRefreshToken()) {
            return;
        }

        try {
            relayPublisher.publishSessionTermination(event.accountId());
        } catch (Exception e) {
            // DB는 이미 커밋됐다. 소켓 정리 실패가 원 작업을 되돌리게 하지 않는다.
            log.error("WebSocket 연결 종료 신호 발행 실패: accountId={}", event.accountId(), e);
        }
    }
}
