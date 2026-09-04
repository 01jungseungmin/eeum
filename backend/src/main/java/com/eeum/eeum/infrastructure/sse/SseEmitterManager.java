package com.eeum.eeum.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE(Server-Sent Events) 연결 관리.
 *
 * <p>연결은 계정 단위로 1개만 유지한다. 동일 계정이 재연결하면 기존 emitter를 대체한다.
 *
 * <p><b>모든 소켓 write는 이 클래스의 전용 풀에서만 일어난다.</b>
 * {@code SseEmitter.send()}는 블로킹 소켓 write다. 클라이언트가 TCP FIN 없이 사라지면
 * (모바일 네트워크 단절, 앱 백그라운드, 프록시 idle cut) 송신 버퍼가 찬 뒤 TCP 재전송
 * 타임아웃까지 스레드가 묶이고, {@code ResponseBodyEmitter.send()}는 {@code synchronized}라
 * 같은 emitter를 만지는 다른 스레드도 함께 묶인다. 호출부는 Tomcat 요청 스레드이거나
 * Redis 리스너 스레드라 거기서 묶이면 요청 처리와 실시간 중계가 통째로 멈춘다.
 * 그래서 호출부는 전송을 <b>큐에 넣고 즉시 돌아간다.</b>
 *
 * <p>풀이 포화되면 전송을 버린다. 못 보낸 배지는 다음 이벤트나 클라이언트의 재조회로
 * 복구되지만, 호출부를 붙잡으면 그 스레드가 수 분간 묶인다. 버리는 쪽이 싸다.
 *
 * <p>하트비트를 주기적으로 보내는 이유는 두 가지다. (1) 죽은 연결을 타임아웃(30분)까지
 * 기다리지 않고 몇 초 안에 감지해 회수한다. (2) 리버스 프록시의 idle timeout에 걸려
 * 연결이 조용히 끊기는 것을 막는다. 하트비트는 SSE 주석(`:`)이라 클라이언트 이벤트
 * 핸들러에 잡히지 않는다.
 */
@Slf4j
@Component
public class SseEmitterManager implements DisposableBean {

    // SSE 연결 타임아웃: 30분 (클라이언트는 끊기면 자동 재연결)
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1_000L;
    // 하트비트 주기. Nginx proxy_read_timeout보다 충분히 짧게 유지해야 프록시가 먼저 끊지 않는다.
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    // accountId → SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 소켓 write 전용 풀. 큐가 차면 새 전송을 버린다(호출부로 예외를 던지지 않는다).
    private final ThreadPoolExecutor writeExecutor = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500),
            runnable -> {
                Thread thread = new Thread(runnable, "sse-write-" + System.nanoTime());
                thread.setDaemon(true);
                return thread;
            },
            (task, executor) -> log.warn("SSE 전송 큐 포화 — 전송을 건너뛴다. active={}, queued={}",
                    executor.getActiveCount(), executor.getQueue().size()));

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

    /**
     * 클라이언트가 SSE 구독을 시작할 때 호출. 기존 연결이 있으면 완료 처리 후 새 emitter로 교체한다.
     *
     * <p>이 메서드는 예외를 던지지 않는다. emitter를 맵에 넣은 뒤 예외가 새어 나가면
     * 컨트롤러는 500을 응답하는데 맵에는 아무도 읽지 않는 emitter가 남고,
     * 그 emitter는 완료된 적이 없어 onCompletion/onError도 돌지 않는다(고아 emitter).
     */
    public SseEmitter subscribe(Long accountId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 콜백을 먼저 건다 — 맵에 넣은 뒤에 걸면 그 사이 종료된 연결이 회수되지 않는다.
        // 정리는 반드시 "이 emitter가 아직 매핑돼 있을 때만" 한다. 무조건 remove(accountId)하면
        // 재연결로 교체된 새 emitter가 지워져(기존 emitter의 onCompletion이 늦게 실행되는 경우)
        // 연결은 살아있는데 unread 이벤트를 못 받는 상태가 된다.
        emitter.onCompletion(() -> {
            emitters.remove(accountId, emitter);
            log.debug("SSE 연결 종료: accountId={}", accountId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(accountId, emitter);
            log.debug("SSE 연결 타임아웃: accountId={}", accountId);
            completeQuietly(accountId, emitter);
        });
        emitter.onError(e -> {
            emitters.remove(accountId, emitter);
            log.debug("SSE 연결 에러: accountId={}, error={}", accountId, e.getMessage());
        });

        SseEmitter previous = emitters.put(accountId, emitter);
        if (previous != null) {
            completeQuietly(accountId, previous);
        }

        // 연결 직후 초기 이벤트 전송 (브라우저 연결 확인용)
        enqueue(accountId, emitter, SseEmitter.event().name("connected").data(accountId), "connected");

        log.debug("SSE 구독 시작: accountId={}, 현재 연결수={}", accountId, emitters.size());
        return emitter;
    }

    // ===================== 이벤트 전송 =====================

    /**
     * 안 읽은 알림 수를 해당 계정의 SSE 채널로 전송. 연결된 emitter가 없으면 스킵한다.
     *
     * <p>큐에 넣고 즉시 반환한다 — 호출부(Redis 리스너 스레드)를 소켓 write에 묶지 않는다.
     */
    public void sendUnreadCount(Long accountId, Object payload) {
        SseEmitter emitter = emitters.get(accountId);
        if (emitter == null) return;

        enqueue(accountId, emitter,
                SseEmitter.event().name("unread-count").data(payload), "unread-count");
    }

    // 해당 계정의 SSE 연결 여부 — 미연결이면 호출자가 payload 조회(DB 쿼리)를 생략할 수 있다
    public boolean isConnected(Long accountId) {
        return emitters.containsKey(accountId);
    }

    // ===================== 내부 헬퍼 =====================

    // 하트비트가 실패한 연결은 그 자리에서 회수된다 — 타임아웃(30분)까지 기다리지 않는다.
    private void sendHeartbeats() {
        try {
            emitters.forEach((accountId, emitter) ->
                    enqueue(accountId, emitter, SseEmitter.event().comment("ping"), "heartbeat"));
        } catch (RuntimeException e) {
            // 여기서 예외가 빠져나가면 scheduleWithFixedDelay가 이후 실행을 영구히 중단한다.
            log.warn("SSE 하트비트 스케줄 실패", e);
        }
    }

    private void enqueue(Long accountId, SseEmitter emitter, SseEmitter.SseEventBuilder event, String what) {
        try {
            writeExecutor.execute(() -> write(accountId, emitter, event, what));
        } catch (RejectedExecutionException e) {
            // 종료 중이면 여기로 온다. 전송 하나 때문에 호출부를 실패시키지 않는다.
            log.debug("SSE 전송 거부: accountId={}, event={}", accountId, what);
        }
    }

    // IOException뿐 아니라 모든 예외를 잡는다. 이미 완료된 emitter에 보내면
    // ResponseBodyEmitter가 IllegalStateException을 던지는데, 이것도 "이 연결은 끝났다"는 뜻이다.
    private void write(Long accountId, SseEmitter emitter, SseEmitter.SseEventBuilder event, String what) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            emitters.remove(accountId, emitter);
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

    // 재기동 시 열린 연결을 정리한다. 정리하지 않으면 클라이언트가 응답이 끝나기를
    // 기다리다 프록시 타임아웃까지 재연결을 시작하지 않는다.
    @Override
    public void destroy() {
        heartbeatScheduler.shutdownNow();
        writeExecutor.shutdown();
        emitters.forEach(this::completeQuietly);
        emitters.clear();
    }
}
