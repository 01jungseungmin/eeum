package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "지역 공개 채팅방 목록 항목")
public class ChatRoomPublicResponseDto {

    private final Long roomId;
    private final ChatRoomType type;
    private final String name;
    @Schema(description = "연관 도메인 타입 (STORE, COMMUNITY, NONE 등)")
    private final ChatRoomRefType refType;
    @Schema(description = "연관 도메인 ID")
    private final Long refId;
    private final long participantCount;
    private final LocalDateTime lastMessageAt;
    private final LocalDateTime createdAt;
    @Schema(description = "현재 사용자가 이미 입장한 방 여부")
    private final boolean joined;

    public static ChatRoomPublicResponseDto of(ChatRoom room, long participantCount, boolean joined) {
        return ChatRoomPublicResponseDto.builder()
                .roomId(room.getChatroomId())
                .type(room.getType())
                .name(room.getName())
                .refType(room.getRefType())
                .refId(room.getRefId())
                .participantCount(participantCount)
                .lastMessageAt(room.getLastMessageAt())
                .createdAt(room.getCreatedAt())
                .joined(joined)
                .build();
    }
}
