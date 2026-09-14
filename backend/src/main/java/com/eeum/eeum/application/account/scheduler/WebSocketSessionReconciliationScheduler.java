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

/** 계정 상태·토큰 세대와 세션을 대조하는 백스톱. 세션이 JVM에만 있어 인스턴스별로 실행한다. */
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
        Map<Long, SseEmitterManager.ConnectionCredentials> sseConnections =
                sseEmitterManager.connectedCredentials();
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

            SseEmitterManager.ConnectionCredentials sseConnection = sseConnections.get(state.accountId());
            if (sseConnection != null && (!state.isUsable(sseConnection.tokenVersion())
                    || tokenService.isFingerprintBlacklisted(sseConnection.tokenFingerprint()))) {
                sseEmitterManager.closeIfCurrent(state.accountId(), sseConnection);
            }
        }

        // 계정이 조회되지 않으면(물리 삭제 등) 붙어 있을 이유가 없다.
        webSocketConnections.keySet().stream()
                .filter(accountId -> states.stream().noneMatch(s -> s.accountId().equals(accountId)))
                .forEach(accountId -> sessionRegistry.closeAll(
                        accountId, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED));
        sseConnections.keySet().stream()
                .filter(accountId -> states.stream().noneMatch(s -> s.accountId().equals(accountId)))
                .forEach(accountId -> sseEmitterManager.closeIfCurrent(
                        accountId, sseConnections.get(accountId)));
    }
}
