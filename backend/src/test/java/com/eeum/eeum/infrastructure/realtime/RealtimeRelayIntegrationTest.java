package com.eeum.eeum.infrastructure.realtime;

import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실시간 중계가 Redis를 건너 실제로 전달되는지 검증한다.
 *
 * <p>알림·채팅은 요청을 처리한 아무 인스턴스에서나 발생하지만 커넥션은 실시간 인스턴스에만 있다.
 * 발행과 수신이 다른 JVM일 수 있다는 것이 이 구조의 전제라 단위 테스트로는 확인할 수 없다.
 * 여기서는 한 JVM 안에서 Redis를 실제로 거쳐 돌아오는지를 본다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class RealtimeRelayIntegrationTest extends IntegrationTestSupport {

    private static final Long ACCOUNT_ID = 4242L;

    private final RealtimeRelayPublisher realtimeRelayPublisher;

    @MockitoBean
    private SseEmitterManager sseEmitterManager;

    @Test
    void unread_카운트는_Redis를_거쳐_구독자에게_전달된다() {
        // given: 이 인스턴스에 SSE 커넥션이 있다고 본다
        when(sseEmitterManager.isConnected(ACCOUNT_ID)).thenReturn(true);

        // when: 발행은 어느 인스턴스에서든 일어날 수 있다
        realtimeRelayPublisher.publishUnreadCount(ACCOUNT_ID, Map.of("total", 7));

        // then: 구독자가 받아 자기 emitter로 밀어낸다
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sseEmitterManager, timeout(5_000)).sendUnreadCount(eq(ACCOUNT_ID), payload.capture());
        assertThat(payload.getValue())
                .isInstanceOfSatisfying(Map.class,
                        map -> assertThat(map).containsEntry("total", 7));
    }

    @Test
    void 이_인스턴스에_커넥션이_없으면_밀어내지_않는다() {
        // given: 커넥션은 다른 인스턴스에 붙어 있다
        when(sseEmitterManager.isConnected(ACCOUNT_ID)).thenReturn(false);

        // when
        realtimeRelayPublisher.publishUnreadCount(ACCOUNT_ID, Map.of("total", 1));

        // then: 수신은 하되 전송하지 않는다
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(sseEmitterManager, never())
                        .sendUnreadCount(eq(ACCOUNT_ID), any()));
    }
}
