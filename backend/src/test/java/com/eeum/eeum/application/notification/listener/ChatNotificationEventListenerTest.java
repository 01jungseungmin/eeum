package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.ChatNotificationProcessor;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotificationEventListenerTest {

    @Mock private ChatNotificationProcessor processor;
    @InjectMocks private ChatNotificationEventListener listener;

    @Test
    void 커밋_후_이벤트를_잠금_프로세서에_위임한다() {
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                7L, "단골 모임방", 1L, "홍길동", "안녕하세요", 500L);

        listener.onMessageSent(event);

        verify(processor).process(event);
    }
}

@ExtendWith(MockitoExtension.class)
class ChatNotificationProcessorTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @Mock
    private ChatUnreadService chatUnreadService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks private ChatNotificationProcessor processor;

    @Test
    void 채팅_메시지_발송시_발신자를_제외한_참여자에게_CHAT_MESSAGE_알림을_생성한다() {
        // given
        Long roomId = 7L;
        Long senderId = 1L;
        Long recipientA = 2L;
        Long recipientB = 3L;
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        when(room.isActive()).thenReturn(true);
        when(chatRoomRepository.findByIdWithPessimisticLock(roomId)).thenReturn(Optional.of(room));
        when(chatParticipantRepository.findActiveAccountIds(roomId))
                .thenReturn(List.of(senderId, recipientA, recipientB));
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                roomId, "단골 모임방", senderId, "홍길동", "안녕하세요", 500L);
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        processor.process(event);

        // then: 발신자 제외 2명에게만 생성, 참조는 CHAT_ROOM/roomId
        verify(notificationService, times(2)).createNotification(captor.capture());
        List<NotificationCreateRequestDto> dtos = captor.getAllValues();
        assertThat(dtos).extracting(NotificationCreateRequestDto::getAccountId)
                .containsExactlyInAnyOrder(recipientA, recipientB)
                .doesNotContain(senderId);
        assertThat(dtos).allSatisfy(dto -> {
            assertThat(dto.getType()).isEqualTo(NotificationType.CHAT_MESSAGE);
            assertThat(dto.getRefType()).isEqualTo(NotificationRefType.CHAT_ROOM);
            assertThat(dto.getRefId()).isEqualTo(roomId);
        });
        verify(chatUnreadService).increment(recipientA, roomId);
        verify(chatUnreadService).increment(recipientB, roomId);
    }

    @Test
    void 종료된_방이면_unread와_알림을_생성하지_않는다() {
        // given: 종료가 먼저 DB 행 잠금을 획득하고 커밋한 뒤 이벤트 처리가 시작된 상황
        Long roomId = 7L;
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        when(room.isActive()).thenReturn(false);
        when(chatRoomRepository.findByIdWithPessimisticLock(roomId)).thenReturn(Optional.of(room));
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                roomId, "종료된 방", 1L, "홍길동", "늦은 메시지", 500L);

        // when
        processor.process(event);

        // then
        verify(chatParticipantRepository, org.mockito.Mockito.never()).findActiveAccountIds(roomId);
        verify(chatUnreadService, org.mockito.Mockito.never())
                .increment(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService, org.mockito.Mockito.never())
                .createNotification(org.mockito.ArgumentMatchers.any());
    }
}
