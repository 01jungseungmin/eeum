package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
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
    private final ChatMessageRepository chatMessageRepository;
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

        // 타입은 수신자와 무관하므로 루프 밖에서 한 번만 판정한다 (첫 문의 판정에 count 쿼리가 붙는다).
        NotificationType type = resolveType(room, event);

        for (Long recipientId : recipientIds) {
            if (recipientId.equals(event.senderAccountId())) {
                continue;
            }

            chatUnreadService.increment(recipientId, event.roomId());
            notificationService.createNotification(NotificationCreateRequestDto.builder()
                    .accountId(recipientId)
                    .type(type)
                    .title(title)
                    .content(content)
                    .refType(NotificationRefType.CHAT_ROOM)
                    .refId(event.roomId())
                    .linkUrl(linkUrl)
                    .build());
        }
    }

    /**
     * 알림 타입 판정.
     *
     * <p>중고 문의방의 <b>첫 메시지를 구매자가 보낸 경우</b>만 {@code USED_PRODUCT_INQUIRY}다.
     * 판매자에게 "문의가 들어왔다"를 알리는 것이 이 타입의 의미이므로, 이후 대화와
     * 판매자가 먼저 말을 건 경우는 일반 채팅 알림으로 둔다.
     *
     * <p>중복 억제는 하지 않는다 — 기존 채팅 알림이 메시지마다 알림을 만드는 정책이고,
     * 중고만 다르게 하면 같은 채팅인데 알림 동작이 갈린다. 억제가 필요하면 채팅 전체 정책으로 다룬다.
     *
     * <p>count 쿼리는 중고 문의방에서만 실행된다 — 일반 채팅에는 부담을 주지 않는다.
     */
    private NotificationType resolveType(ChatRoom room, ChatMessageSentEvent event) {
        if (!room.isUsedProductRoom()) {
            return NotificationType.CHAT_MESSAGE;
        }
        boolean sentByBuyer = room.getBuyerAccountId() != null
                && room.getBuyerAccountId().equals(event.senderAccountId());
        if (!sentByBuyer) {
            return NotificationType.CHAT_MESSAGE;
        }
        // 방 생성 시 시스템 메시지를 남기지 않으므로, 방금 저장된 메시지가 유일하면 첫 문의다.
        boolean firstMessage = chatMessageRepository.countByChatRoom_ChatroomId(event.roomId()) == 1;
        return firstMessage ? NotificationType.USED_PRODUCT_INQUIRY : NotificationType.CHAT_MESSAGE;
    }
}
