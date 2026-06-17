package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomRead(ChatRoomReadEvent event) {
        chatUnreadService.resetRoom(event.accountId(), event.roomId());
        log.debug("채팅 unread 리셋: accountId={}, roomId={}", event.accountId(), event.roomId());
    }
}
