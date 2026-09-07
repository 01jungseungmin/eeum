package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.ChatRedisKeys;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.file.FileUploadPurpose;
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
import com.eeum.eeum.application.notification.service.NotificationOutboxDispatcher;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.application.notification.service.NotificationOutboxRecorder;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.domain.chat.repository.ChatMessageCursor;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int PREVIEW_MAX_LENGTH = 50;

    private final AccountWriteGuard accountWriteGuard;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatAccessHelper chatAccessHelper;
    private final ChatUnreadService chatUnreadService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationOutboxRecorder outboxRecorder;
    private final StringRedisTemplate redisTemplate;
    private final FileStorageService fileStorageService;

    // ===================== 메시지 발송 =====================

    // 텍스트 메시지 발송
    @Transactional
    public ChatMessageResponseDto sendMessage(
            Long accountId, Long roomId, ChatMessageSendRequestDto request) {
        checkIdempotency(accountId, roomId, request.getClientMessageId());

        // 발신자 계정을 먼저 잠근다(account → chat_room, 프로젝트 전역 순서).
        // 잠그지 않고 로딩된 엔티티에 assertWritable()만 호출하면, 탈퇴 트랜잭션과 겹쳤을 때
        // 탈퇴 이전 상태를 읽은 이 트랜잭션이 탈퇴 커밋 뒤에 메시지를 남긴다.
        // WebSocket 인터셉터의 사전 검증은 트랜잭션 밖이라 이 경쟁을 막지 못한다.
        accountWriteGuard.lockActive(accountId);

        // 종료와 같은 방 행을 잠근 뒤 활성 상태를 확인한다.
        // 확인 후 종료가 끼어드는 check-then-act 경쟁을 DB 커밋까지 차단한다.
        ChatRoom room = chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId);
        chatAccessHelper.verifyRoomActive(room);
        ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);

        Account sender = participant.getAccount();
        // 위 lockActive가 계정 행을 잠그고 상태를 확인했다. 참여자를 통해 얻은 이 인스턴스가
        // 같은 계정인지에 기대지 않고 한 번 더 본다 — 이미 로딩돼 있어 추가 조회가 없다.
        sender.assertWritable();
        // 첫 메시지 여부는 lastMessageAt을 갱신하기 전에 확정한다.
        // 이 방 행은 위에서 비관적 잠금으로 읽었으므로 스냅샷이 아니라 최신 커밋 값이고,
        // 같은 방으로 동시에 들어온 발송은 이 잠금에서 직렬화된다.
        boolean firstMessage = room.getLastMessageAt() == null;

        ChatMessage message = ChatMessage.text(room, sender, request.getContent());
        chatMessageRepository.save(message);
        room.updateLastMessageAt(message.getSentAt());

        ChatMessageResponseDto dto = toResponse(message);

        eventPublisher.publishEvent(
                new ChatMessageBroadcastEvent(room.getChatroomId(), dto)
        );

        publishSentEvent(room, sender, message, request.getContent(), firstMessage);

        return dto;
    }

    // 이미지 메시지 발송 (클라이언트가 S3 업로드 후 URL 전달)
    // WS/REST 구분 없이 이 이벤트로 브로드캐스트 — WS 핸들러는 직접 broadcast 하지 않음
    @Transactional
    public ChatMessageResponseDto sendImageMessage(
            Long accountId, Long roomId, ChatImageMessageSendRequestDto request) {
        checkIdempotency(accountId, roomId, request.getClientMessageId());

        // 텍스트 발송과 같은 순서로 잠근다 — account → chat_room.
        accountWriteGuard.lockActive(accountId);

        ChatRoom room = chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId);
        chatAccessHelper.verifyRoomActive(room);
        ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);

        // file_object 잠금은 마지막이다. 먼저 잡으면 file_object를 쥔 채 account를 기다리게 되어
        // 다른 이미지 경로(used/community/store)와 순서가 엇갈린다.
        fileStorageService.requireAttachableObject(accountId, FileUploadPurpose.CHAT, request.getImageUrl());

        Account sender = participant.getAccount();
        // 위 lockActive가 계정 행을 잠그고 상태를 확인했다. 참여자를 통해 얻은 이 인스턴스가
        // 같은 계정인지에 기대지 않고 한 번 더 본다 — 이미 로딩돼 있어 추가 조회가 없다.
        sender.assertWritable();
        boolean firstMessage = room.getLastMessageAt() == null;

        ChatMessage message = ChatMessage.image(room, sender, request.getImageUrl());
        chatMessageRepository.save(message);
        room.updateLastMessageAt(message.getSentAt());

        ChatMessageResponseDto dto = toResponse(message);
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(room.getChatroomId(), dto));
        publishSentEvent(room, sender, message, "사진을 보냈습니다", firstMessage);
        return dto;
    }

    // ===================== 조회 =====================

    /**
     * 메시지 목록 (최신→과거) — 커서 무한 스크롤.
     *
     * <p>커서는 발신 시각과 메시지 ID를 함께 담는다. 예전에는 시각 하나였고 조건이
     * {@code sentAt < cursor}라, 같은 시각에 저장된 메시지가 경계에 걸리면 나머지가
     * 영구히 누락됐다.
     *
     * @param cursorValue 직전 응답의 {@code nextCursorValue}. 첫 페이지면 null이다.
     * @param cursorId    직전 응답의 {@code nextCursorId}. 첫 페이지면 null이다.
     */
    @Transactional(readOnly = true)
    public CursorSlice<ChatMessageResponseDto> getMessages(
            Long accountId, Long roomId, String cursorValue, Long cursorId, int size) {
        // 커서 조립은 여기서 한다 — 컨트롤러가 리포지토리 패키지를 참조하지 않도록(LayerRuleTest).
        ChatMessageCursor cursor = ChatMessageCursor.ofNullable(cursorValue, cursorId);
        chatAccessHelper.verifyParticipant(accountId, roomId);

        return chatMessageRepository.findRoomMessages(roomId, cursor, size)
                .map(this::toResponse);
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
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(roomId, toResponse(message)));
        log.info("채팅 메시지 삭제: messageId={}, accountId={}", messageId, accountId);
    }

    // ===================== 내부 헬퍼 =====================

    // clientMessageId가 있으면 5분 TTL로 Redis에 NX 저장 — 중복 요청 차단
    private void checkIdempotency(Long accountId, Long roomId, String clientMessageId) {
        if (clientMessageId == null) {
            return;
        }
        String key = ChatRedisKeys.messageIdempotency(accountId, roomId, clientMessageId);
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

    /**
     * 알림 요청을 outbox에 남긴다 — 메시지 저장과 같은 트랜잭션에서 커밋된다.
     *
     * <p>예전에는 여기서 Spring 이벤트를 발행하고 {@code AFTER_COMMIT} + {@code @Async}가
     * 알림을 만들었다. 비동기 풀이 포화되면 그 작업이 버려져 알림이 아예 생기지 않았고,
     * 메시지 전송은 성공으로 끝나 아무도 알아채지 못했다.
     *
     * <p>브로드캐스트({@code ChatMessageBroadcastEvent})는 그대로 이벤트로 둔다.
     * 실시간 전달은 놓쳐도 다음 조회에서 복구되지만, 알림 레코드는 놓치면 복구되지 않는다.
     */
    private void publishSentEvent(
            ChatRoom room, Account sender, ChatMessage message,
            String rawPreview, boolean firstMessage) {
        outboxRecorder.record(
                NotificationOutboxDispatcher.CHAT_MESSAGE_SENT,
                new ChatMessageSentEvent(
                        room.getChatroomId(),
                        room.getName(),
                        sender.getAccountId(),
                        // 푸시 알림 제목·본문에 실린다 — 실명이 잠금화면에 뜨면 안 된다
                        sender.getDisplayName(),
                        truncate(rawPreview),
                        message.getChatmessageId(),
                        firstMessage));
    }

    // 채팅 unread의 DB 기준값 — Redis 캐시 미스 복구와 정합성 보정 스케줄러가 공유한다.
    // 활성 방만 센다: 종료된 방은 목록에서 빠지고 구독도 막혀 사용자가 읽어서 회수할 수 없으므로,
    // 여기에 포함하면 캐시가 사라질 때마다 회수 불가능한 배지가 되살아난다.
    public long sumUnreadFromDb(Long accountId) {
        return chatParticipantRepository
                .findActiveParticipationsInActiveRooms(accountId, ParticipantStatus.ACTIVE)
                .stream()
                .mapToLong(p -> chatMessageRepository.countByChatRoom_ChatroomIdAndSentAtAfterAndAccount_AccountIdNot(
                        p.getChatRoom().getChatroomId(), p.unreadSince(), accountId))
                .sum();
    }

    private ChatMessageResponseDto toResponse(ChatMessage message) {
        return ChatMessageResponseDto.from(message);
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
