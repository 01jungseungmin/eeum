package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 메시지 알림 생성과 채팅방 종료를 DB 행 잠금으로 직렬화한다.
 * 메시지가 먼저 잠그면 알림 커밋 후 종료 정리가 수행되고,
 * 종료가 먼저 잠그면 비활성 상태를 확인해 unread/알림 생성을 생략한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatNotificationProcessor {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatUnreadService chatUnreadService;
    private final NotificationService notificationService;

    @Transactional
    public void process(ChatMessageSentEvent event) {
        ChatRoom room = chatRoomRepository.findByIdWithPessimisticLock(event.roomId())
                .orElse(null);
        if (room == null || !room.isActive()) {
            log.debug("종료되거나 삭제된 채팅방 메시지 — unread/알림 생략: roomId={}", event.roomId());
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

            chatUnreadService.increment(recipientId, event.roomId());
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
