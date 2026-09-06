package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
 * <p>증상: private S3 objectKey가 STOMP로 그대로 전달돼 이미지 메시지를 표시할 수 없었다.
 * 결함 위치: ChatBroadcastEventListener.onBroadcast.
 * 이 테스트는 브로드캐스트 payload가 조회 가능한 URL을 사용함을 고정한다.
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
        ChatMessageBroadcastEvent event = new ChatMessageBroadcastEvent(roomId, payload);

        // When
        chatBroadcastEventListener.onBroadcast(event);

        // Then
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/" + roomId, payload);
        verifyNoMoreInteractions(realtimeRelayPublisher);
    }

    @Test
    void 이미지_메시지_브로드캐스트에는_조회_가능한_presigned_URL을_전송한다() {
        // Given — private S3 objectKey는 STOMP 구독자가 직접 조회할 수 없다.
        Long roomId = 20L;
        ChatMessageResponseDto payload = ChatMessageResponseDto.builder()
                .messageId(1L)
                .roomId(roomId)
                .senderAccountId(2L)
                .senderName("판매자")
                .senderProfileImageUrl("profiles/2/profile.webp")
                .imageUrl("chat/2/message.webp")
                .messageType(MessageType.IMAGE)
                .deleted(false)
                .build();
        given(fileStorageService.resolveImageUrl("profiles/2/profile.webp"))
                .willReturn("https://signed.example/profile");
        given(fileStorageService.resolveImageUrl("chat/2/message.webp"))
                .willReturn("https://signed.example/message");
        given(fileStorageService.isFinalObjectKey("profiles/2/profile.webp")).willReturn(true);
        given(fileStorageService.isFinalObjectKey("chat/2/message.webp")).willReturn(true);
        ChatMessageBroadcastEvent event = new ChatMessageBroadcastEvent(roomId, payload);

        // When
        chatBroadcastEventListener.onBroadcast(event);

        // Then
        org.mockito.ArgumentCaptor<ChatMessageResponseDto> captor =
                org.mockito.ArgumentCaptor.forClass(ChatMessageResponseDto.class);
        verify(realtimeRelayPublisher).publishStomp(
                org.mockito.ArgumentMatchers.eq("/sub/chat/rooms/20"), captor.capture());
        assertThat(captor.getValue().getSenderProfileImageUrl()).isEqualTo("https://signed.example/profile");
        assertThat(captor.getValue().getImageUrl()).isEqualTo("https://signed.example/message");
    }

    @Test
    void 브로드캐스트_이벤트_다른_roomId는_독립된_destination으로_전송() {
        // Given
        Long roomIdA = 10L;
        Long roomIdB = 99L;
        ChatMessageResponseDto payloadA = mock(ChatMessageResponseDto.class);
        ChatMessageResponseDto payloadB = mock(ChatMessageResponseDto.class);

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

        // When
        chatBroadcastEventListener.onBroadcast(new ChatMessageBroadcastEvent(roomId, payload));

        // Then — /sub/chat/rooms/{roomId} 형식 준수
        verify(realtimeRelayPublisher).publishStomp("/sub/chat/rooms/1", payload);
    }
}
