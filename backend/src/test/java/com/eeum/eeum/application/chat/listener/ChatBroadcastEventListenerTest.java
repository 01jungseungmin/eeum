package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link ChatBroadcastEventListener} 단위 테스트.
 *
 * 브로커로 직접 밀지 않고 Redis 중계로 발행한다 — 메시지를 만든 인스턴스와 구독자가 붙은
 * 인스턴스가 다를 수 있다. 실제 왕복은 RealtimeRelayIntegrationTest에서 검증한다.
 *
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 위상은 Spring 트랜잭션 인프라가 관리하므로
 * 단위 테스트에서는 메서드 동작(destination · payload 전달)만 검증한다.
 * AFTER_COMMIT 실행 보장은 통합 테스트 범위로 분류한다.
 *
 * <p>이미지 URL 변환은 DB 커밋 뒤 이 리스너에서 수행한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatBroadcastEventListenerTest {

    @InjectMocks
    private ChatBroadcastEventListener chatBroadcastEventListener;

    @Mock private RealtimeRelayPublisher realtimeRelayPublisher;
    @Mock private FileStorageService fileStorageService;

    // ===================== onBroadcast =====================

    @Test
    void 브로드캐스트_이벤트_올바른_destination으로_payload_전송() {
        // Given
        Long roomId = 10L;
        ChatMessageResponseDto payload = mock(ChatMessageResponseDto.class);
        givenRawImageUrls(payload);
        ChatMessageBroadcastEvent event = new ChatMessageBroadcastEvent(roomId, payload);

        // When
        chatBroadcastEventListener.onBroadcast(event);

        // Then
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/" + roomId, payload);
        verifyNoMoreInteractions(realtimeRelayPublisher);
    }

    @Test
    void 브로드캐스트_이벤트_다른_roomId는_독립된_destination으로_전송() {
        // Given
        Long roomIdA = 10L;
        Long roomIdB = 99L;
        ChatMessageResponseDto payloadA = mock(ChatMessageResponseDto.class);
        ChatMessageResponseDto payloadB = mock(ChatMessageResponseDto.class);
        givenRawImageUrls(payloadA);
        givenRawImageUrls(payloadB);

        // When
        chatBroadcastEventListener.onBroadcast(new ChatMessageBroadcastEvent(roomIdA, payloadA));
        chatBroadcastEventListener.onBroadcast(new ChatMessageBroadcastEvent(roomIdB, payloadB));

        // Then — roomId별 독립 채널로 분리됨
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/10", payloadA);
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/99", payloadB);
        verifyNoMoreInteractions(realtimeRelayPublisher);
    }

    @Test
    void 브로드캐스트_이벤트_destination_경로_형식_검증() {
        // Given
        Long roomId = 1L;
        ChatMessageResponseDto payload = mock(ChatMessageResponseDto.class);
        givenRawImageUrls(payload);

        // When
        chatBroadcastEventListener.onBroadcast(new ChatMessageBroadcastEvent(roomId, payload));

        // Then — /sub/chat/rooms/{roomId} 형식 준수
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/1", payload);
    }

    @Test
    void final_이미지_key를_STOMP_전송_전에_조회_URL로_변환한다() {
        // Given
        ChatMessageResponseDto payload = mock(ChatMessageResponseDto.class);
        ChatMessageResponseDto resolvedPayload = mock(ChatMessageResponseDto.class);
        when(payload.getSenderProfileImageUrl()).thenReturn("profiles/2/profile.webp");
        when(payload.getImageUrl()).thenReturn("chat/2/message.webp");
        when(fileStorageService.isFinalObjectKey("profiles/2/profile.webp")).thenReturn(true);
        when(fileStorageService.isFinalObjectKey("chat/2/message.webp")).thenReturn(true);
        when(fileStorageService.resolveImageUrl("profiles/2/profile.webp"))
                .thenReturn("https://signed.example/profile");
        when(fileStorageService.resolveImageUrl("chat/2/message.webp"))
                .thenReturn("https://signed.example/message");
        when(payload.withResolvedImageUrls("https://signed.example/profile", "https://signed.example/message"))
                .thenReturn(resolvedPayload);

        // When
        chatBroadcastEventListener.onBroadcast(new ChatMessageBroadcastEvent(10L, payload));

        // Then
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/10", resolvedPayload);
        verify(fileStorageService).resolveImageUrl("profiles/2/profile.webp");
        verify(fileStorageService).resolveImageUrl("chat/2/message.webp");
    }

    private void givenRawImageUrls(ChatMessageResponseDto payload) {
        when(payload.getSenderProfileImageUrl()).thenReturn(null);
        when(payload.getImageUrl()).thenReturn(null);
        when(payload.withResolvedImageUrls(null, null)).thenReturn(payload);
    }
}
