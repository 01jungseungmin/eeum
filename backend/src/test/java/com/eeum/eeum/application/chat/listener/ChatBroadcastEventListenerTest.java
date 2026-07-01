package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link ChatBroadcastEventListener} 단위 테스트.
 *
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 위상은 Spring 트랜잭션 인프라가 관리하므로
 * 단위 테스트에서는 메서드 동작(destination · payload 전달)만 검증한다.
 * AFTER_COMMIT 실행 보장은 통합 테스트 범위로 분류한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatBroadcastEventListenerTest {

    @InjectMocks
    private ChatBroadcastEventListener chatBroadcastEventListener;

    @Mock private SimpMessagingTemplate messagingTemplate;

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
        verify(messagingTemplate).convertAndSend("/sub/chat/rooms/" + roomId, payload);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void 브로드캐스트_이벤트_payload_변환없이_그대로_전송() {
        // Given — 리스너는 relay 역할이므로 payload를 변환하지 않아야 한다
        Long roomId = 20L;
        ChatMessageResponseDto payload = mock(ChatMessageResponseDto.class);
        ChatMessageBroadcastEvent event = new ChatMessageBroadcastEvent(roomId, payload);

        // When
        chatBroadcastEventListener.onBroadcast(event);

        // Then
        verify(messagingTemplate).convertAndSend("/sub/chat/rooms/20", payload);
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
        verify(messagingTemplate).convertAndSend("/sub/chat/rooms/10", payloadA);
        verify(messagingTemplate).convertAndSend("/sub/chat/rooms/99", payloadB);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void 브로드캐스트_이벤트_destination_경로_형식_검증() {
        // Given
        Long roomId = 1L;
        ChatMessageResponseDto payload = mock(ChatMessageResponseDto.class);

        // When
        chatBroadcastEventListener.onBroadcast(new ChatMessageBroadcastEvent(roomId, payload));

        // Then — /sub/chat/rooms/{roomId} 형식 준수
        verify(messagingTemplate).convertAndSend("/sub/chat/rooms/1", payload);
    }
}
