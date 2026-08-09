package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.service.ChatNotificationProcessor;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 채팅 메시지 발송 이벤트를 처리한다.
 * 채팅 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되어
 * 발신자를 제외한 ACTIVE 참여자의 채팅 unread 카운트를 증가시키고 알림(푸시)을 발행한다.
 */
@Component
@RequiredArgsConstructor
public class ChatNotificationEventListener {

    private final ChatNotificationProcessor chatNotificationProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        chatNotificationProcessor.process(event);
    }
}
