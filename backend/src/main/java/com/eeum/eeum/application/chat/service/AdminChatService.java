package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 채팅 모니터링/모더레이션 (REST). 신고 연동은 신고 도메인 구현 시 확장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    // 전체 채팅방 조회 (타입/활성/기간 필터)
    @Transactional(readOnly = true)
    public Page<ChatRoomResponseDto> getAllRooms(ChatRoomAdminSearchDto condition, Pageable pageable) {
        return chatRoomRepository.searchRoomsByAdmin(condition, pageable)
                .map(room -> {
                    long participantCount = chatParticipantRepository
                            .countByChatRoom_ChatroomIdAndStatus(room.getChatroomId(), ParticipantStatus.ACTIVE);
                    String preview = chatMessageRepository
                            .findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(room.getChatroomId())
                            .map(ChatMessage::getContent)
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
        log.info("[ADMIN] 채팅 메시지 강제 삭제: messageId={}", messageId);
    }

    // 채팅방 강제 비활성화
    @Transactional
    public void forceDeactivateRoom(Long roomId) {
        ChatRoom room = getRoomOrThrow(roomId);
        room.deactivate();
        log.info("[ADMIN] 채팅방 강제 비활성화: roomId={}", roomId);
    }

    private ChatRoom getRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
