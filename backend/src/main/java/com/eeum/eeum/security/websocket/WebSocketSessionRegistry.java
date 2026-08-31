package com.eeum.eeum.security.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 계정별 WebSocket 세션 레지스트리 — 제재·탈퇴 시 이미 열린 연결을 끊기 위해 필요하다.
 *
 * <p>인바운드 프레임(CONNECT/SUBSCRIBE/SEND)에는 계정 상태 검사가 있지만, <b>수신은 인바운드가
 * 아니다.</b> 이미 구독을 걸어둔 연결은 상대가 보낸 메시지·읽음·타이핑 이벤트를 계속 받는다.
 * 정지된 계정이 대화를 계속 들여다볼 수 있다는 뜻이고, 인터셉터로는 막을 수 없다.
 *
 * <p><b>단일 인스턴스 전제.</b> 인메모리 STOMP 브로커와 같은 전제다({@code WebSocketConfig} 참고) —
 * 세션은 그 커넥션을 받은 JVM에만 존재하므로 다른 인스턴스의 세션은 끊지 못한다.
 * 인스턴스를 늘리면 Redis 릴레이({@code RealtimeRelayPublisher})를 태워야 한다.
 *
 * <p>계정 바인딩과 세션 등록 시점이 다르다. 세션은 HTTP 업그레이드 직후 등록되는데
 * 그때는 아직 STOMP CONNECT 전이라 누구인지 모른다. 계정은 CONNECT를 인증한 뒤에 붙인다.
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    /** 클라이언트에 "계정 상태가 바뀌어 끊었다"를 알리는 코드. 네트워크 오류와 구분하려고 앱 정의 범위를 쓴다. */
    public static final CloseStatus ACCOUNT_STATE_CHANGED = new CloseStatus(4001, "ACCOUNT_STATE_CHANGED");

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionIdsByAccount = new ConcurrentHashMap<>();

    // HTTP 업그레이드 직후. 아직 인증 전이라 계정은 모른다.
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    // STOMP CONNECT 인증 후. 한 계정이 여러 기기로 붙을 수 있어 집합으로 둔다.
    public void bindAccount(String sessionId, Long accountId) {
        if (sessionId == null || accountId == null) {
            return;
        }
        sessionIdsByAccount
                .computeIfAbsent(accountId, id -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    /**
     * 연결 종료 정리. 정상 종료든 비정상 종료든 이 경로로 온다.
     *
     * <p>계정 인덱스에서도 빼야 한다 — 남겨두면 끊긴 세션 ID가 쌓여 누수가 된다.
     */
    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        sessionIdsByAccount.values().forEach(ids -> ids.remove(sessionId));
        sessionIdsByAccount.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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

    // 테스트·운영 점검용
    public int sessionCount(Long accountId) {
        Set<String> ids = sessionIdsByAccount.get(accountId);
        return ids == null ? 0 : ids.size();
    }
}
