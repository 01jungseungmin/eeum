package com.eeum.eeum.infrastructure.sse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SseEmitterManagerTest {

    private SseEmitterManager sseEmitterManager;

    private static final Long ACCOUNT_ID = 6L;

    @BeforeEach
    void setUp() {
        sseEmitterManager = new SseEmitterManager();
    }

    @AfterEach
    void tearDown() {
        sseEmitterManager.destroy();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, SseEmitter> emitters() {
        return (Map<Long, SseEmitter>) ReflectionTestUtils.getField(sseEmitterManager, "emitters");
    }

    private SseEmitter registerMockEmitter(Long accountId) {
        SseEmitter emitter = mock(SseEmitter.class);
        emitters().put(accountId, emitter);
        return emitter;
    }

    @Test
    void 전송이_IOException으로_실패하면_emitter를_회수한다() throws Exception {
        // given
        SseEmitter emitter = registerMockEmitter(ACCOUNT_ID);
        doThrow(new IOException("broken pipe"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        sseEmitterManager.sendUnreadCount(ACCOUNT_ID, Map.of("unreadCount", 3));

        // then: 죽은 연결을 맵에 남기면 이후 이벤트가 매번 그 소켓으로 향한다
        await().untilAsserted(() -> assertThat(sseEmitterManager.isConnected(ACCOUNT_ID)).isFalse());
    }

    // IOException만 잡던 시절에는 이 경로에서 예외가 전송 스레드로 빠져나가고
    // emitter는 맵에 그대로 남았다. 이미 완료된 emitter에 보내면 실제로 이 예외가 난다.
    @Test
    void 전송이_IOException이_아닌_예외로_실패해도_emitter를_회수한다() throws Exception {
        // given
        SseEmitter emitter = registerMockEmitter(ACCOUNT_ID);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        sseEmitterManager.sendUnreadCount(ACCOUNT_ID, Map.of("unreadCount", 3));

        // then
        await().untilAsserted(() -> assertThat(sseEmitterManager.isConnected(ACCOUNT_ID)).isFalse());
    }

    @Test
    void 전송은_호출_스레드가_아닌_전용_풀에서_수행된다() throws Exception {
        // given
        SseEmitter emitter = registerMockEmitter(ACCOUNT_ID);

        // when
        sseEmitterManager.sendUnreadCount(ACCOUNT_ID, Map.of("unreadCount", 1));

        // then: 호출부는 큐에 넣고 즉시 돌아가고, write는 sse-write 스레드에서 일어난다.
        // 요청 스레드에서 직접 write하면 죽은 클라이언트의 TCP 재전송 타임아웃까지 묶인다.
        verify(emitter, timeout(2_000)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 연결이_없는_계정에는_전송하지_않는다() {
        // when & then
        assertThatCode(() -> sseEmitterManager.sendUnreadCount(999L, Map.of("unreadCount", 1)))
                .doesNotThrowAnyException();
        assertThat(sseEmitterManager.isConnected(999L)).isFalse();
    }

    @Test
    void 같은_계정이_재연결하면_이전_emitter를_완료하고_교체한다() {
        // given
        SseEmitter previous = registerMockEmitter(ACCOUNT_ID);

        // when
        SseEmitter current = sseEmitterManager.subscribe(ACCOUNT_ID);

        // then
        verify(previous).complete();
        assertThat(emitters().get(ACCOUNT_ID)).isSameAs(current);
    }

    // 이전 emitter 정리가 실패했다고 새 구독을 실패시키면, 맵에는 emitter가 남고
    // 컨트롤러는 500을 응답한다 — 아무도 읽지 않는 고아 emitter가 생긴다.
    @Test
    void 이전_emitter_정리가_실패해도_구독은_성공한다() {
        // given
        SseEmitter previous = registerMockEmitter(ACCOUNT_ID);
        doThrow(new IllegalStateException("already completed")).when(previous).complete();

        // when & then
        assertThatCode(() -> sseEmitterManager.subscribe(ACCOUNT_ID)).doesNotThrowAnyException();
        assertThat(sseEmitterManager.isConnected(ACCOUNT_ID)).isTrue();
    }

    @Test
    void 구독하면_해당_계정이_연결된_것으로_보인다() {
        // when
        SseEmitter emitter = sseEmitterManager.subscribe(ACCOUNT_ID);

        // then
        assertThat(emitter).isNotNull();
        assertThat(sseEmitterManager.isConnected(ACCOUNT_ID)).isTrue();
    }

    @Test
    void 종료시_열린_연결을_모두_정리한다() {
        // given
        SseEmitter emitter = registerMockEmitter(ACCOUNT_ID);

        // when
        sseEmitterManager.destroy();

        // then: 정리하지 않으면 클라이언트가 응답 종료를 기다리다 재연결이 늦어진다
        verify(emitter).complete();
        assertThat(sseEmitterManager.isConnected(ACCOUNT_ID)).isFalse();
    }
}
