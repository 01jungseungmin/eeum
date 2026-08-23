package com.eeum.eeum.api;

import com.eeum.eeum.api.notification.NotificationController;
import com.eeum.eeum.api.notification.NotificationSseController;
import com.eeum.eeum.application.chat.listener.ChatBroadcastEventListener;
import com.eeum.eeum.config.WebSocketConfig;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelaySubscriber;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 전용 인스턴스(실시간 비활성)가 정상 기동하는지 검증한다.
 * <p>
 * WebSocket 설정을 끄면 {@code SimpMessagingTemplate} 빈이 사라진다.
 * 그 빈에 의존하는 STOMP 수신·중계 수신 경로를 함께 끄지 않으면 컨텍스트 자체가 뜨지 않는다 —
 * 배포해서 서버가 안 뜨고 나서야 알게 되는 종류의 실패라 기동 자체를 테스트로 고정한다.
 *
 * <p>반대로 중계 <b>발행</b>은 모든 인스턴스에서 떠야 한다. 함께 꺼버리면 API 인스턴스에서
 * 만들어진 채팅·알림이 실시간 인스턴스로 전달되지 않는다.
 */
@EnabledIfDockerAvailable
@SpringBootTest(properties = "eeum.realtime.enabled=false")
@RequiredArgsConstructor
class RealtimeDisabledContextTest extends IntegrationTestSupport {

    private final ApplicationContext applicationContext;

    @Test
    void 실시간을_끄면_WebSocket과_SSE_빈이_뜨지_않는다() {
        assertThat(applicationContext.getBeanNamesForType(WebSocketConfig.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(NotificationSseController.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(RealtimeRelaySubscriber.class)).isEmpty();
    }

    @Test
    void 실시간을_꺼도_중계_발행은_모든_인스턴스에서_동작한다() {
        // 채팅 메시지는 REST로도 만들어진다. 발행까지 꺼버리면 API 인스턴스에서 보낸 메시지가
        // 실시간 인스턴스의 구독자에게 영영 닿지 못한다. 발행은 켜고 수신만 끈다.
        assertThat(applicationContext.getBeanNamesForType(ChatBroadcastEventListener.class)).isNotEmpty();
        assertThat(applicationContext.getBeanNamesForType(RealtimeRelayPublisher.class)).isNotEmpty();
    }

    @Test
    void 실시간을_꺼도_일반_알림_API는_그대로_동작한다() {
        // 알림 목록·읽음 처리는 실시간과 무관하게 모든 인스턴스에서 떠야 한다
        assertThat(applicationContext.getBeanNamesForType(NotificationController.class)).isNotEmpty();
    }
}
