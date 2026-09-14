package com.eeum.eeum.security.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 계정별 WebSocket 세션 레지스트리 — 제재·탈퇴 시 이미 열린 연결을 끊기 위해 필요하다. */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    /** 클라이언트에 "계정 상태가 바뀌어 끊었다"를 알리는 코드. 네트워크 오류와 구분하려고 앱 정의 범위를 쓴다. */
    public static final CloseStatus ACCOUNT_STATE_CHANGED = new CloseStatus(4001, "ACCOUNT_STATE_CHANGED");

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionIdsByAccount = new ConcurrentHashMap<>();
    // 한 계정의 여러 기기가 서로 다른 세대·토큰으로 연결될 수 있어 세션별로 보관한다.
    private final Map<String, ConnectionCredentials> credentialsBySession = new ConcurrentHashMap<>();

    // HTTP 업그레이드 직후. 아직 인증 전이라 계정은 모른다.
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    // STOMP CONNECT 인증 후. 한 계정이 여러 기기로 붙을 수 있어 집합으로 둔다.
    public void bindAccount(
            String sessionId,
            Long accountId,
            Long tokenVersion,
            String tokenFingerprint,
            long tokenExpiresAtEpochMilli
    ) {
        if (sessionId == null || accountId == null) {
            return;
        }
        sessionIdsByAccount
                .computeIfAbsent(accountId, id -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        credentialsBySession.put(sessionId, new ConnectionCredentials(
                accountId, tokenVersion, tokenFingerprint, tokenExpiresAtEpochMilli));
    }

    /** 이 인스턴스에 붙어 있는 세션과 연결 시점의 인증 정보. 주기적 대조용. */
    public Map<String, ConnectionCredentials> connectedCredentials() {
        return Map.copyOf(credentialsBySession);
    }

    /**
     * 연결 종료 정리. 정상 종료든 비정상 종료든 이 경로로 온다.
     *
     * 계정 인덱스에서도 빼야 한다 — 남겨두면 끊긴 세션 ID가 쌓여 누수가 된다.
     */
    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        ConnectionCredentials credentials = credentialsBySession.remove(sessionId);
        if (credentials != null) {
            removeAccountIndex(credentials.accountId(), sessionId);
        }
    }

    /**
     * 해당 계정의 모든 연결을 끊는다.
     *
     * @return 실제로 끊은 세션 수
     */
    public int closeAll(Long accountId, CloseStatus status) {
        Set<String> sessionIds = sessionIdsByAccount.remove(accountId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }

        int closed = 0;
        for (String sessionId : sessionIds) {
            credentialsBySession.remove(sessionId);
            WebSocketSession session = sessions.remove(sessionId);
            if (session == null) {
                continue;
            }
            try {
                session.close(status);
                closed++;
            } catch (IOException | IllegalStateException e) {
                // 이미 끊긴 세션일 수 있다. 나머지 세션 정리를 막지 않는다.
                log.warn("WebSocket 세션 종료 실패: accountId={}, sessionId={}", accountId, sessionId, e);
            }
        }
        return closed;
    }

    /** 대조 때 본 인증 정보가 아직 같은 세션에 붙어 있을 때만 해당 연결을 끊는다. */
    public boolean closeIfCurrent(
            String sessionId,
            ConnectionCredentials expectedCredentials,
            CloseStatus status
    ) {
        if (!credentialsBySession.remove(sessionId, expectedCredentials)) {
            return false;
        }
        removeAccountIndex(expectedCredentials.accountId(), sessionId);
        WebSocketSession session = sessions.remove(sessionId);
        if (session == null) {
            return false;
        }
        try {
            session.close(status);
            return true;
        } catch (IOException | IllegalStateException e) {
            log.warn("WebSocket 세션 종료 실패: accountId={}, sessionId={}",
                    expectedCredentials.accountId(), sessionId, e);
            return false;
        }
    }

    private void removeAccountIndex(Long accountId, String sessionId) {
        sessionIdsByAccount.computeIfPresent(accountId, (id, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
    }

    // 테스트·운영 점검용
    public int sessionCount(Long accountId) {
        Set<String> ids = sessionIdsByAccount.get(accountId);
        return ids == null ? 0 : ids.size();
    }

    public record ConnectionCredentials(
            Long accountId,
            Long tokenVersion,
            String tokenFingerprint,
            long tokenExpiresAtEpochMilli
    ) {
    }
}
