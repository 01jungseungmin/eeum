package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
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
    private final ChatRoomRepository chatRoomRepository;
    private final ChatUnreadService chatUnreadService;
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        // 종료 직전에 시작된 메시지 트랜잭션이 종료 이후에 커밋되면 이 리스너가 종료된 방의 unread를 올린다.
        // 그 방은 목록에서 빠지고 구독도 막혀 사용자가 읽어서 회수할 방법이 없으므로 배지가 영구히 남는다.
        if (!chatRoomRepository.existsByChatroomIdAndIsActiveTrue(event.roomId())) {
            log.debug("종료된 채팅방 메시지 — unread/알림 생략: roomId={}", event.roomId());
            return;
        }

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
