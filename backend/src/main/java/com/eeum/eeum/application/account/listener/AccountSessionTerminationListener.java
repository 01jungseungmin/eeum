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
 * <p><b>이 리스너는 모든 인스턴스에서 뜬다.</b> 세션은 커넥션을 받은 실시간 인스턴스에만 있지만
 * 제재는 아무 API 인스턴스에서나 일어나고, Spring 이벤트는 JVM-local이라 그 사이를 넘지 못한다.
 * 그래서 여기서는 종료 신호를 Redis로 중계만 하고, 실제 종료는 세션을 가진 인스턴스가 한다
 * (메시지 브로드캐스트와 같은 구조 — {@code RealtimeRelayPublisher} 참고).
 *
 * <p>Pub/Sub은 유실될 수 있다. 최종 보장은 이 경로가 아니라 계정에 남긴 무효화 시각이 맡는다.
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
