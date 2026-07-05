package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "채팅방 상세 (참여자 목록 포함)")
public class ChatRoomDetailResponseDto {

    private final Long roomId;
    private final ChatRoomType type;
    private final String name;
    private final boolean active;
    private final Long createdBy;
    private final LocalDateTime createdAt;
    private final int participantCount;
    private final List<ChatParticipantResponseDto> participants;

    public static ChatRoomDetailResponseDto of(
            ChatRoom room,
            List<ChatParticipantResponseDto> participants
    ) {
        return ChatRoomDetailResponseDto.builder()
                .roomId(room.getChatroomId())
                .type(room.getType())
                .name(room.getName())
                .active(room.isActive())
                .createdBy(room.getCreator().getAccountId())
                .createdAt(room.getCreatedAt())
                .participantCount(participants.size())
                .participants(participants)
                .build();
    }
}
