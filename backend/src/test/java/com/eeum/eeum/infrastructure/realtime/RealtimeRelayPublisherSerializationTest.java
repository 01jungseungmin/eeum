package com.eeum.eeum.infrastructure.realtime;

import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomClosedResponseDto;
import com.eeum.eeum.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 회귀 테스트 — 운영에서 채팅 메시지가 DB에는 저장되지만 실시간으로 오지 않던 장애.
 *
 * 전역 ObjectMapper에 날짜 모듈이 없어 sentAt 직렬화가 매번 실패했고, 발행기가 예외를
 * warn 로그로 삼켜 조용히 유실됐다. 기존 중계 테스트는 날짜 없는 unread Map만 보내 잡지 못했다.
 */
@ExtendWith(MockitoExtension.class)
class RealtimeRelayPublisherSerializationTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 9, 19, 2, 48, 16, 539_516_000);

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void 날짜가_있는_채팅_메시지도_Redis로_발행된다() throws Exception {
        // given
        RealtimeRelayPublisher publisher = new RealtimeRelayPublisher(redisTemplate, objectMapper);
        ChatMessageResponseDto message = ChatMessageResponseDto.builder()
                .messageId(910255L)
                .roomId(910001L)
                .senderAccountId(901000L)
                .content("오늘도 좋은 하루 되세요")
                .sentAt(SENT_AT)
                .build();

        // when
        publisher.publishStomp("/sub/chat/rooms/910001", message);

        // then
        Map<?, ?> payload = relayedPayload();
        assertThat(payload.get("messageId")).isEqualTo(910255);
        // REST 응답과 같은 ISO 문자열이어야 클라이언트가 같은 코드로 날짜를 읽는다
        assertThat(payload.get("sentAt")).isEqualTo("2026-09-19T02:48:16.539516");
    }

    @Test
    void 채팅방_종료_통지도_Redis로_발행된다() throws Exception {
        // given
        RealtimeRelayPublisher publisher = new RealtimeRelayPublisher(redisTemplate, objectMapper);

        // when
        publisher.publishStomp("/sub/chat/rooms/910001/closed",
                ChatRoomClosedResponseDto.of(910001L, 901000L, SENT_AT));

        // then
        assertThat(relayedPayload().get("closedAt")).isEqualTo("2026-09-19T02:48:16.539516");
    }

    // 구독 측(RealtimeRelaySubscriber)과 같은 방식으로 되읽는다
    private Map<?, ?> relayedPayload() throws Exception {
        ArgumentCaptor<String> published = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(RealtimeRelayChannels.STOMP), published.capture());
        StompRelayMessage relayed = objectMapper.readValue(published.getValue(), StompRelayMessage.class);
        return objectMapper.readValue(relayed.payloadJson(), Map.class);
    }
}
