package com.eeum.eeum.application.chat.helper;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채팅 도메인 공통 접근 검증 헬퍼.
 */
@Component
@RequiredArgsConstructor
public class ChatAccessHelper {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 채팅방 조회
    public ChatRoom getRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    // 채팅방 ACTIVE 참여자 검증
    public ChatParticipant verifyParticipant(Long accountId, Long roomId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT));
        if (!participant.isActive()) {
            throw new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }
        return participant;
    }

    // 메시지 발신자 본인 검증
    public ChatMessage verifyMessageOwnership(Long accountId, Long messageId) {
        return chatMessageRepository
                .findByChatmessageIdAndAccount_AccountId(messageId, accountId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.CHAT_MESSAGE_ACCESS_DENIED));
    }

    // GROUP/GROUP_STREET 타입 검증 (PRIVATE 초대 차단)
    public void verifyGroupRoom(ChatRoom room) {
        if (!room.isGroup()) {
            throw new BadRequestException(ErrorCode.CHAT_NOT_GROUP_ROOM);
        }
    }

    // 채팅방 활성 상태 검증
    public void verifyRoomActive(ChatRoom room) {
        if (!room.isActive()) {
            throw new BadRequestException(ErrorCode.CHAT_ROOM_INACTIVE);
        }
    }
}
