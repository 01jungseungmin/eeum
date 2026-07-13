package com.eeum.eeum.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// SSE(Server-Sent Events) 연결 관리
// 연결은 계정 단위로 1개만 유지한다. 동일 계정이 재연결하면 기존 emitter를 대체
@Slf4j
@Component
public class SseEmitterManager {

    // SSE 연결 타임아웃: 30분 (클라이언트는 끊기면 자동 재연결)
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1_000L;

    // accountId → SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ===================== 연결 =====================

    // 클라이언트가 SSE 구독을 시작할 때 호출 기존 연결이 있으면 완료(close) 처리 후 새 emitter로 교체
    // @param accountId 구독자 account ID @return 새로 생성된 SseEmitter
    public SseEmitter subscribe(Long accountId) {
        // 기존 emitter 정리
        SseEmitter existing = emitters.remove(accountId);
        if (existing != null) {
            existing.complete();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 연결 종료/에러 시 맵에서 제거 — 반드시 "이 emitter가 아직 매핑돼 있을 때만" 제거한다.
        // 무조건 remove(accountId)하면 재연결로 교체된 새 emitter가 지워져(기존 emitter의 onCompletion이
        // 늦게 실행되는 경우) 연결은 살아있는데 unread 이벤트를 못 받는 상태가 된다.
        emitter.onCompletion(() -> {
            emitters.remove(accountId, emitter);
            log.debug("SSE 연결 종료: accountId={}", accountId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(accountId, emitter);
            log.debug("SSE 연결 타임아웃: accountId={}", accountId);
            emitter.complete();
        });
        emitter.onError(e -> {
            emitters.remove(accountId, emitter);
            log.debug("SSE 연결 에러: accountId={}, error={}", accountId, e.getMessage());
        });

        emitters.put(accountId, emitter);

        // 연결 직후 초기 이벤트 전송 (브라우저 연결 확인용)
        sendEvent(accountId, emitter, "connected", accountId);

        log.debug("SSE 구독 시작: accountId={}, 현재 연결수={}", accountId, emitters.size());
        return emitter;
    }

    // ===================== 이벤트 전송 =====================

    //안 읽은 알림 수를 해당 계정의 SSE 채널로 전송 연결된 emitter가 없으면 스킵
    // @param accountId 수신 계정 ID @param count 안 읽은 알림 수

    public void sendUnreadCount(Long accountId, long count) {
        SseEmitter emitter = emitters.get(accountId);
        if (emitter == null) return;

        sendEvent(accountId, emitter, "unread-count", count);
    }

    // ===================== 내부 헬퍼 =====================

    private void sendEvent(Long accountId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(eventName)
                            .data(data)
            );
        } catch (IOException e) {
            emitters.remove(accountId, emitter);
            log.debug("SSE 이벤트 전송 실패 — emitter 제거: accountId={}", accountId);
        }
    }
}
