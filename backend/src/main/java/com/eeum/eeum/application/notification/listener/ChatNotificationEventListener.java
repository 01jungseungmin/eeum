package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 채팅 메시지 발송 이벤트를 처리한다.
 * 채팅 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되어
 * 발신자를 제외한 ACTIVE 참여자의 채팅 unread 카운트를 증가시키고 알림(푸시)을 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationEventListener {

    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatUnreadService chatUnreadService;
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        List<Long> recipientIds = chatParticipantRepository.findActiveAccountIds(event.roomId());

        String title = event.roomName() != null ? event.roomName() : "새 메시지";
        String content = String.format("%s: %s", event.senderName(), event.preview());
        String linkUrl = "/chat/rooms/" + event.roomId();

        for (Long recipientId : recipientIds) {
            if (recipientId.equals(event.senderAccountId())) {
                continue;
            }
            // 채팅 전용 unread 배지 증가
            chatUnreadService.increment(recipientId, event.roomId());

            // 알림함 + 푸시 (chatEnabled 설정/DND는 NotificationService에서 처리)
            notificationService.createNotification(NotificationCreateRequestDto.builder()
                    .accountId(recipientId)
                    .type(NotificationType.CHAT_MESSAGE)
                    .title(title)
                    .content(content)
                    .refType(NotificationRefType.CHAT_ROOM)
                    .refId(event.roomId())
                    .linkUrl(linkUrl)
                    .build());
        }
    }
}
