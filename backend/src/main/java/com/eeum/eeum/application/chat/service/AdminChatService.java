package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.helper.ChatMessagePreview;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 관리자 채팅 모니터링/모더레이션 (REST). 신고 연동은 신고 도메인 구현 시 확장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final FileStorageService fileStorageService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomService chatRoomService;
    private final ApplicationEventPublisher eventPublisher;

    // 전체 채팅방 조회 (타입/활성/기간 필터) — 배치 쿼리로 N+1 제거
    @Transactional(readOnly = true)
    public Page<ChatRoomResponseDto> getAllRooms(ChatRoomAdminSearchDto condition, Pageable pageable) {
        Page<ChatRoom> rooms = chatRoomRepository.searchRoomsByAdmin(condition, pageable);
        if (rooms.isEmpty()) {
            return rooms.map(room -> ChatRoomResponseDto.of(room, null, 0L, 0L));
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getChatroomId).toList();

        Map<Long, Long> participantCounts = chatParticipantRepository
                .countGroupedByRoomIdsAndStatus(roomIds, ParticipantStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Map<Long, ChatMessage> latestMessages = chatMessageRepository
                .findLatestMessagesForRooms(roomIds)
                .stream()
                .collect(Collectors.toMap(
                        msg -> msg.getChatRoom().getChatroomId(),
                        msg -> msg,
                        (a, b) -> a.getSentAt().isAfter(b.getSentAt()) ? a : b
                ));

        return rooms.map(room -> {
            long participantCount = participantCounts.getOrDefault(room.getChatroomId(), 0L);
            String preview = Optional.ofNullable(latestMessages.get(room.getChatroomId()))
                    .map(ChatMessagePreview::of)
                    .orElse(null);
            return ChatRoomResponseDto.of(room, preview, 0L, participantCount);
        });
    }

    // 채팅방 메시지 조회 (신고 처리용)
    @Transactional(readOnly = true)
    public Page<ChatMessageResponseDto> getRoomMessages(Long roomId, Pageable pageable) {
        getRoomOrThrow(roomId);
        return chatMessageRepository
                .findAllByChatRoom_ChatroomIdOrderBySentAtDesc(roomId, pageable)
                .map(ChatMessageResponseDto::from);
    }

    // 메시지 강제 삭제 (Soft Delete)
    @Transactional
    public void forceDeleteMessage(Long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        message.markDeleted();
        Long roomId = message.getChatRoom().getChatroomId();
        eventPublisher.publishEvent(
                new ChatMessageBroadcastEvent(roomId, ChatMessageResponseDto.from(message)));
        log.info("[ADMIN] 채팅 메시지 강제 삭제: messageId={}", messageId);
    }

    // 채팅방 강제 비활성화 — ChatRoomService에 위임한다.
    // 여기서 직접 deactivate()하면 (1) 생성과 같은 분산 락을 잡지 않아 재생성 요청과 레이스가 나고
    // (2) Redis unread 회수/종료 통지가 빠져 사장 종료와 다른 잔여 상태를 남긴다.
    // @Transactional을 걸지 않는 이유: 위임 대상이 "락 획득 → 트랜잭션" 순서를 직접 관리한다.
    public void forceDeactivateRoom(Long roomId) {
        chatRoomService.forceCloseRoom(roomId);
    }

    private ChatRoom getRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
