package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.ChatRedisKeys;
import com.eeum.eeum.application.chat.dto.request.ChatImageMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatUnreadCountResponseDto;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int PREVIEW_MAX_LENGTH = 50;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatAccessHelper chatAccessHelper;
    private final ChatUnreadService chatUnreadService;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    // ===================== 메시지 발송 =====================

    // 텍스트 메시지 발송
    @Transactional
    public ChatMessageResponseDto sendMessage(
            Long accountId, Long roomId, ChatMessageSendRequestDto request) {
        checkIdempotency(request.getClientMessageId());

        ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);
        ChatRoom room = participant.getChatRoom();
        chatAccessHelper.verifyRoomActive(room);

        Account sender = participant.getAccount();
        ChatMessage message = ChatMessage.text(room, sender, request.getContent());
        chatMessageRepository.save(message);
        room.updateLastMessageAt(message.getSentAt());

        ChatMessageResponseDto dto = ChatMessageResponseDto.from(message);

        eventPublisher.publishEvent(
                new ChatMessageBroadcastEvent(room.getChatroomId(), dto)
        );

        publishSentEvent(room, sender, message, request.getContent());

        return dto;
    }

    // 이미지 메시지 발송 (클라이언트가 S3 업로드 후 URL 전달)
    // WS/REST 구분 없이 이 이벤트로 브로드캐스트 — WS 핸들러는 직접 broadcast 하지 않음
    @Transactional
    public ChatMessageResponseDto sendImageMessage(
            Long accountId, Long roomId, ChatImageMessageSendRequestDto request) {
        checkIdempotency(request.getClientMessageId());

        ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);
        ChatRoom room = participant.getChatRoom();
        chatAccessHelper.verifyRoomActive(room);

        Account sender = participant.getAccount();
        ChatMessage message = ChatMessage.image(room, sender, request.getImageUrl());
        chatMessageRepository.save(message);
        room.updateLastMessageAt(message.getSentAt());

        ChatMessageResponseDto dto = ChatMessageResponseDto.from(message);
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(room.getChatroomId(), dto));
        publishSentEvent(room, sender, message, "사진을 보냈습니다");
        return dto;
    }

    // ===================== 조회 =====================

    // 메시지 목록 (최신→과거) — 커서 기반 무한 스크롤
    // cursor: 이전 페이지의 마지막 메시지 sentAt (null이면 첫 페이지)
    @Transactional(readOnly = true)
    public Slice<ChatMessageResponseDto> getMessages(
            Long accountId, Long roomId, LocalDateTime cursor, int size) {
        chatAccessHelper.verifyParticipant(accountId, roomId);
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<ChatMessage> raw = (cursor == null)
                ? chatMessageRepository.findAllByChatRoom_ChatroomIdOrderBySentAtDesc(roomId, pageable).getContent()
                : chatMessageRepository.findAllByChatRoom_ChatroomIdAndSentAtBeforeOrderBySentAtDesc(roomId, cursor, pageable);
        boolean hasNext = raw.size() > size;
        List<ChatMessageResponseDto> content = raw.stream()
                .limit(size)
                .map(ChatMessageResponseDto::from)
                .toList();
        return new SliceImpl<>(content, pageable, hasNext);
    }

    // 전체 안 읽은 메시지 수 (Redis 우선, 캐시 미스 시 DB 합산)
    @Transactional(readOnly = true)
    public ChatUnreadCountResponseDto countUnread(Long accountId) {
        long count = chatUnreadService.getTotalUnread(accountId, () -> sumUnreadFromDb(accountId));
        return ChatUnreadCountResponseDto.of(count);
    }

    // ===================== 삭제 =====================

    // 본인 메시지 Soft Delete — 삭제 후 같은 방 참여자에게 "삭제된 메시지" 상태 브로드캐스트
    @Transactional
    public void deleteMessage(Long accountId, Long messageId) {
        ChatMessage message = chatAccessHelper.verifyMessageOwnership(accountId, messageId);
        if (!message.isDeletable(accountId)) {
            throw new BadRequestException(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE);
        }
        message.markDeleted();
        Long roomId = message.getChatRoom().getChatroomId();
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(roomId, ChatMessageResponseDto.from(message)));
        log.info("채팅 메시지 삭제: messageId={}, accountId={}", messageId, accountId);
    }

    // ===================== 내부 헬퍼 =====================

    // clientMessageId가 있으면 5분 TTL로 Redis에 NX 저장 — 중복 요청 차단
    private void checkIdempotency(String clientMessageId) {
        if (clientMessageId == null) {
            return;
        }
        String key = ChatRedisKeys.messageIdempotency(clientMessageId);
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(isNew)) {
            throw new ConflictException(ErrorCode.CHAT_MESSAGE_DUPLICATE);
        }
        // 트랜잭션이 롤백되면 메시지는 저장되지 않았으므로 멱등 키를 되돌린다 — 그대로 두면 5분 TTL 동안
        // 동일 clientMessageId의 정상 재시도가 CHAT_MESSAGE_DUPLICATE로 거부되어 메시지가 조용히 유실된다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        redisTemplate.delete(key);
                    }
                }
            });
        }
    }

    private void publishSentEvent(ChatRoom room, Account sender, ChatMessage message, String rawPreview) {
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                room.getChatroomId(),
                room.getName(),
                sender.getAccountId(),
                sender.getName(),
                truncate(rawPreview),
                message.getChatmessageId()
        ));
    }

    private long sumUnreadFromDb(Long accountId) {
        return chatParticipantRepository
                .findAllByAccount_AccountIdAndStatus(accountId, ParticipantStatus.ACTIVE)
                .stream()
                .mapToLong(p -> chatMessageRepository.countByChatRoom_ChatroomIdAndSentAtAfterAndAccount_AccountIdNot(
                        p.getChatRoom().getChatroomId(), p.unreadSince(), accountId))
                .sum();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > PREVIEW_MAX_LENGTH
                ? text.substring(0, PREVIEW_MAX_LENGTH) + "…"
                : text;
    }
}
