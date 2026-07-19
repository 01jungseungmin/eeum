package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUnreadResetEventListener {

    private final ChatUnreadService chatUnreadService;
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomRead(ChatRoomReadEvent event) {
        chatUnreadService.resetRoom(event.accountId(), event.roomId());

        // 채팅 배지 리셋만으로는 알림함의 CHAT_MESSAGE가 미읽음으로 남아
        // SSE unread-count의 CHAT 카테고리가 줄지 않는다 — 해당 방 알림도 함께 읽음 처리
        notificationService.markAsReadByRef(
                event.accountId(),
                NotificationType.CHAT_MESSAGE,
                NotificationRefType.CHAT_ROOM,
                event.roomId());

        log.debug("채팅 unread 리셋: accountId={}, roomId={}", event.accountId(), event.roomId());
    }
}
