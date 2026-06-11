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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

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

    // 텍스트 메시지 발송 (REST 폴백 — WebSocket 도입 전 기본 경로)
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

        publishSentEvent(room, sender, message, request.getContent());
        return ChatMessageResponseDto.from(message);
    }

    // 이미지 메시지 발송 (클라이언트가 S3 업로드 후 URL 전달)
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

        publishSentEvent(room, sender, message, "사진을 보냈습니다");
        return ChatMessageResponseDto.from(message);
    }

    // ===================== 조회 =====================

    // 메시지 목록 (최신→과거). isDeleted=true는 "삭제된 메시지"로 표시
    @Transactional(readOnly = true)
    public Page<ChatMessageResponseDto> getMessages(Long accountId, Long roomId, Pageable pageable) {
        chatAccessHelper.verifyParticipant(accountId, roomId);
        return chatMessageRepository
                .findAllByChatRoom_ChatroomIdOrderBySentAtDesc(roomId, pageable)
                .map(ChatMessageResponseDto::from);
    }

    // 전체 안 읽은 메시지 수 (Redis 우선, 캐시 미스 시 DB 합산)
    @Transactional(readOnly = true)
    public ChatUnreadCountResponseDto countUnread(Long accountId) {
        long count = chatUnreadService.getTotalUnread(accountId, () -> sumUnreadFromDb(accountId));
        return ChatUnreadCountResponseDto.of(count);
    }

    // ===================== 삭제 =====================

    // 본인 메시지 Soft Delete
    @Transactional
    public void deleteMessage(Long accountId, Long messageId) {
        ChatMessage message = chatAccessHelper.verifyMessageOwnership(accountId, messageId);
        if (!message.isDeletable(accountId)) {
            throw new BadRequestException(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE);
        }
        message.markDeleted();
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
                .mapToLong(p -> {
                    LocalDateTime since = p.getLastReadTime() != null
                            ? p.getLastReadTime()
                            : p.getJoinedAt();
                    return chatMessageRepository.countByChatRoom_ChatroomIdAndSentAtAfter(
                            p.getChatRoom().getChatroomId(), since);
                })
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
