package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refresh Token을 회수하면 열린 WebSocket 연결도 함께 끊는다.
 *
 * 수신은 인바운드가 아니라 상태 검사를 거치지 않는다 — 이미 구독한 연결은 정지·탈퇴 후에도
 * Access Token 수명 동안 대화를 계속 받았다. 기준을 제재가 아니라 토큰 회수로 잡아
 * 본인 탈퇴·비밀번호 재설정·사장 승격도 포함한다. 세션은 실시간 인스턴스에만 있고
 * Spring 이벤트는 JVM-local이라 Redis로 중계하며, 최종 보장은 계정의 무효화 시각이 맡는다.
 */
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
