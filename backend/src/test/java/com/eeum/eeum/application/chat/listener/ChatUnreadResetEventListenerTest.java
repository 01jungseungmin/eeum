package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatUnreadResetEventListenerTest {

    @Mock
    private ChatUnreadService chatUnreadService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChatUnreadResetEventListener listener;

    @Test
    void 채팅방_읽음시_채팅_배지_리셋과_해당_방의_CHAT_MESSAGE_알림_읽음_처리를_함께_수행한다() {
        // given
        Long accountId = 5L;
        Long roomId = 7L;

        // when
        listener.onRoomRead(new ChatRoomReadEvent(accountId, roomId));

        // then
        verify(chatUnreadService).resetRoom(accountId, roomId);
        verify(notificationService).markAsReadByRef(
                accountId, NotificationType.CHAT_MESSAGE, NotificationRefType.CHAT_ROOM, roomId);
    }
}
