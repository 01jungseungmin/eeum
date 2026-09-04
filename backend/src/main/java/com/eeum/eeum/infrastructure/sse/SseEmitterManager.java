package com.eeum.eeum.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// 계정별 SSE 연결과 전송 큐를 관리한다.
@Slf4j
@Component
public class SseEmitterManager implements DisposableBean {

    // SSE 연결 타임아웃: 30분 (클라이언트는 끊기면 자동 재연결)
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1_000L;
    // 하트비트 주기. Nginx proxy_read_timeout보다 충분히 짧게 유지해야 프록시가 먼저 끊지 않는다.
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    // accountId → SSE 연결과 인증 스냅샷. 분리된 Map으로 관리하면 재연결 경합에서
    // 새 emitter와 이전 토큰 정보가 섞일 수 있어 하나의 값으로 교체한다.
    private final Map<Long, Connection> connections = new ConcurrentHashMap<>();

    // 소켓 write 전용 풀. 큐가 차면 새 전송을 버린다(호출부로 예외를 던지지 않는다).
    private final ThreadPoolExecutor writeExecutor = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500),
            runnable -> {
                Thread thread = new Thread(runnable, "sse-write-" + System.nanoTime());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    // 하트비트는 "언제 보낼지"만 정한다. 실제 write는 writeExecutor로 넘겨,
    // 죽은 연결 하나가 나머지 전원의 하트비트를 막지 못하게 한다.
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public SseEmitterManager() {
        heartbeatScheduler.scheduleWithFixedDelay(
                this::sendHeartbeats,
                HEARTBEAT_INTERVAL.toMillis(),
                HEARTBEAT_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    // ===================== 연결 =====================

    // 기존 연결은 교체하고 새 emitter를 반환한다.
    public SseEmitter subscribe(Long accountId) {
        return subscribe(accountId, 0L, "");
    }

    public SseEmitter subscribe(Long accountId, Long tokenVersion, String tokenFingerprint) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Connection connection = new Connection(
                emitter,
                tokenVersion == null ? 0L : tokenVersion,
                tokenFingerprint == null ? "" : tokenFingerprint);

        // 콜백을 먼저 건다 — 맵에 넣은 뒤에 걸면 그 사이 종료된 연결이 회수되지 않는다.
        // 정리는 반드시 "이 emitter가 아직 매핑돼 있을 때만" 한다. 무조건 remove(accountId)하면
        // 재연결로 교체된 새 emitter가 지워져(기존 emitter의 onCompletion이 늦게 실행되는 경우)
        // 연결은 살아있는데 unread 이벤트를 못 받는 상태가 된다.
        emitter.onCompletion(() -> {
            removeConnection(accountId, connection);
            log.debug("SSE 연결 종료: accountId={}", accountId);
        });
        emitter.onTimeout(() -> {
            removeConnection(accountId, connection);
            log.debug("SSE 연결 타임아웃: accountId={}", accountId);
            completeQuietly(accountId, emitter);
        });
        emitter.onError(e -> {
            removeConnection(accountId, connection);
            log.debug("SSE 연결 에러: accountId={}, error={}", accountId, e.getMessage());
        });

        Connection previous = connections.put(accountId, connection);
        if (previous != null) {
            completeQuietly(accountId, previous.emitter());
        }

        // 연결 직후 초기 이벤트 전송 (브라우저 연결 확인용)
        enqueue(accountId, emitter, SseEmitter.event().name("connected").data(accountId), "connected");

        log.debug("SSE 구독 시작: accountId={}, 현재 연결수={}", accountId, connections.size());
        return emitter;
    }

    // ===================== 이벤트 전송 =====================

    // 최신 unread payload만 남기고 계정별로 직렬 전송한다.
    public void sendUnreadCount(Long accountId, Object payload) {
        Connection connection = connections.get(accountId);
        if (connection == null) return;

        connection.pendingUnreadPayload().set(payload);
        scheduleUnreadWrite(accountId, connection);
    }

    // 해당 계정의 SSE 연결 여부 — 미연결이면 호출자가 payload 조회(DB 쿼리)를 생략할 수 있다
    public boolean isConnected(Long accountId) {
        return connections.containsKey(accountId);
    }

    public Map<Long, ConnectionCredentials> connectedCredentials() {
        Map<Long, ConnectionCredentials> credentials = new HashMap<>();
        connections.forEach((accountId, connection) ->
                credentials.put(accountId, connection.credentials()));
        return Map.copyOf(credentials);
    }

    // 초기 unread 조회가 실패한 경우, 같은 계정이 재연결한 뒤 그 새 연결을 지우지 않도록
    // emitter가 일치할 때만 정리한다.
    public void closeIfCurrent(Long accountId, SseEmitter emitter) {
        Connection connection = connections.get(accountId);
        if (connection != null && connection.emitter() == emitter) {
            removeConnection(accountId, connection);
            completeQuietly(accountId, emitter);
        }
    }

    public void closeAll(Long accountId) {
        Connection connection = connections.remove(accountId);
        if (connection != null) {
            completeQuietly(accountId, connection.emitter());
        }
    }

    public void closeIfCurrent(Long accountId, ConnectionCredentials credentials) {
        Connection connection = connections.get(accountId);
        if (connection != null && connection.credentials().equals(credentials)
                && connections.remove(accountId, connection)) {
            completeQuietly(accountId, connection.emitter());
        }
    }

    // ===================== 내부 헬퍼 =====================

    // 하트비트가 실패한 연결은 그 자리에서 회수된다 — 타임아웃(30분)까지 기다리지 않는다.
    private void sendHeartbeats() {
        try {
            connections.forEach((accountId, connection) ->
                    enqueue(accountId, connection.emitter(), SseEmitter.event().comment("ping"), "heartbeat"));
        } catch (RuntimeException e) {
            // 여기서 예외가 빠져나가면 scheduleWithFixedDelay가 이후 실행을 영구히 중단한다.
            log.warn("SSE 하트비트 스케줄 실패", e);
        }
    }

    private void enqueue(Long accountId, SseEmitter emitter, SseEmitter.SseEventBuilder event, String what) {
        try {
            writeExecutor.execute(() -> write(accountId, emitter, event, what));
        } catch (RejectedExecutionException e) {
            log.warn("SSE 전송 큐 포화 — 전송을 건너뛴다: accountId={}, event={}", accountId, what);
        }
    }

    private void scheduleUnreadWrite(Long accountId, Connection connection) {
        if (!connection.unreadWriteInFlight().compareAndSet(false, true)) {
            return;
        }
        try {
            writeExecutor.execute(() -> flushUnreadCount(accountId, connection));
        } catch (RejectedExecutionException e) {
            connection.unreadWriteInFlight().set(false);
            connection.pendingUnreadPayload().set(null);
            log.warn("SSE unread 전송 큐 포화 — 최신 배지를 건너뛴다: accountId={}", accountId);
        }
    }

    private void flushUnreadCount(Long accountId, Connection connection) {
        try {
            while (true) {
                if (connections.get(accountId) != connection) {
                    return;
                }

                Object payload = connection.pendingUnreadPayload().getAndSet(null);
                if (payload == null) {
                    return;
                }
                try {
                    connection.emitter().send(SseEmitter.event().name("unread-count").data(payload));
                } catch (Exception e) {
                    removeConnection(accountId, connection);
                    return;
                }
            }
        } finally {
            connection.unreadWriteInFlight().set(false);
            if (connections.get(accountId) == connection
                    && connection.pendingUnreadPayload().get() != null) {
                scheduleUnreadWrite(accountId, connection);
            }
        }
    }

    // IOException뿐 아니라 모든 예외를 잡는다. 이미 완료된 emitter에 보내면
    // ResponseBodyEmitter가 IllegalStateException을 던지는데, 이것도 "이 연결은 끝났다"는 뜻이다.
    private void write(Long accountId, SseEmitter emitter, SseEmitter.SseEventBuilder event, String what) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            Connection connection = connections.get(accountId);
            if (connection != null && connection.emitter() == emitter) {
                removeConnection(accountId, connection);
            }
            log.debug("SSE 전송 실패 — emitter 제거: accountId={}, event={}, error={}",
                    accountId, what, e.getMessage());
        }
    }

    private void completeQuietly(Long accountId, SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE 연결 정리 실패 — 무시: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    private void removeConnection(Long accountId, Connection connection) {
        connections.remove(accountId, connection);
    }

    // 재기동 시 열린 연결을 정리한다. 정리하지 않으면 클라이언트가 응답이 끝나기를
    // 기다리다 프록시 타임아웃까지 재연결을 시작하지 않는다.
    @Override
    public void destroy() {
        heartbeatScheduler.shutdownNow();
        writeExecutor.shutdown();
        connections.forEach((accountId, connection) -> completeQuietly(accountId, connection.emitter()));
        connections.clear();
    }

    static final class Connection {
        private final SseEmitter emitter;
        private final ConnectionCredentials credentials;
        private final AtomicReference<Object> pendingUnreadPayload = new AtomicReference<>();
        private final AtomicBoolean unreadWriteInFlight = new AtomicBoolean();

        Connection(SseEmitter emitter, long tokenVersion, String tokenFingerprint) {
            this.emitter = emitter;
            this.credentials = new ConnectionCredentials(tokenVersion, tokenFingerprint);
        }

        SseEmitter emitter() {
            return emitter;
        }

        ConnectionCredentials credentials() {
            return credentials;
        }

        AtomicReference<Object> pendingUnreadPayload() {
            return pendingUnreadPayload;
        }

        AtomicBoolean unreadWriteInFlight() {
            return unreadWriteInFlight;
        }
    }

    public record ConnectionCredentials(long tokenVersion, String tokenFingerprint) {
    }
}
