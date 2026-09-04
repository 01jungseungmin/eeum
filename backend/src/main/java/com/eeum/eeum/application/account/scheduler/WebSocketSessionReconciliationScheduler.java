package com.eeum.eeum.application.account.scheduler;

import com.eeum.eeum.common.scheduling.InstanceLocalSchedule;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.domain.account.repository.AccountAuthState;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * 붙어 있는 WebSocket 세션을 계정 상태·토큰 세대와 주기적으로 대조해 끊는다.
 *
 * <p>정상 경로는 제재 시점의 즉시 종료다({@code AccountSessionTerminationListener} → Redis 중계).
 * 이건 <b>백스톱</b>이다. 그 신호는 여러 이유로 사라진다 — 발행이 비동기 풀 포화로 폐기되거나,
 * Redis Pub/Sub이 구독자 중단 중에 유실되거나, 실시간 인스턴스가 재시작되거나, 망이 끊긴다.
 *
 * <p>durable stream 대신 대조를 택한 이유는 <b>왜 놓쳤는지와 무관하게 복구</b>되기 때문이다.
 * 전달 채널을 튼튼하게 만드는 방식은 채널 유실만 막지, 재시작이나 분단은 못 덮는다.
 *
 * <p>토큰 세대까지 보는 이유는 상태만으로 부족해서다. 비밀번호 재설정·사장 승인은 계정을
 * ACTIVE로 남기므로, 상태만 비교하면 회수된 토큰으로 연결된 세션이 그대로 살아남는다.
 *
 * <p>실시간 커넥션을 가진 인스턴스에서만 뜬다 — 다른 인스턴스에는 끊을 세션이 없다.
 *
 * <p><b>분산 잠금을 걸지 않는다</b>({@link InstanceLocalSchedule}). 세션은 그 커넥션을 받은
 * 인스턴스의 JVM에만 있으므로, 잠금으로 한 대만 돌게 하면 나머지 인스턴스의 세션이 방치된다.
 * 이중 처리가 아니라 미처리가 문제가 되는 쪽이다.
 *
 * <p><b>스케줄러 스레드는 기본 1개다.</b> 여기가 느려지면 다른 스케줄러가 전부 밀린다.
 * 그래서 붙어 있는 계정 ID로 IN 조회 한 번만 하고 끝낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketSessionReconciliationScheduler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final SseEmitterManager sseEmitterManager;
    private final AccountRepository accountRepository;
    private final TokenService tokenService;

    @Scheduled(fixedDelayString = "${eeum.realtime.session-reconcile-interval-ms:30000}")
    @InstanceLocalSchedule
    public void closeRevokedSessions() {
        Map<Long, Long> webSocketConnections = sessionRegistry.connectedTokenVersions();
        Map<Long, Long> sseConnections = sseEmitterManager.connectedTokenVersions();
        if (webSocketConnections.isEmpty() && sseConnections.isEmpty()) {
            return;
        }

        Set<Long> accountIds = new HashSet<>(webSocketConnections.keySet());
        accountIds.addAll(sseConnections.keySet());
        List<AccountAuthState> states = accountRepository.findAuthStates(accountIds);

        for (AccountAuthState state : states) {
            Long webSocketVersion = webSocketConnections.get(state.accountId());
            if (webSocketVersion != null && !state.isUsable(webSocketVersion)) {
                sessionRegistry.closeAll(state.accountId(), WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
            }

            Long sseVersion = sseConnections.get(state.accountId());
            if (sseVersion != null && (!state.isUsable(sseVersion)
                    || tokenService.isFingerprintBlacklisted(sseEmitterManager.tokenFingerprint(state.accountId())))) {
                sseEmitterManager.closeAll(state.accountId());
            }
        }

        // 계정이 조회되지 않으면(물리 삭제 등) 붙어 있을 이유가 없다.
        webSocketConnections.keySet().stream()
                .filter(accountId -> states.stream().noneMatch(s -> s.accountId().equals(accountId)))
                .forEach(accountId -> sessionRegistry.closeAll(
                        accountId, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED));
        sseConnections.keySet().stream()
                .filter(accountId -> states.stream().noneMatch(s -> s.accountId().equals(accountId)))
                .forEach(sseEmitterManager::closeAll);
    }
}
