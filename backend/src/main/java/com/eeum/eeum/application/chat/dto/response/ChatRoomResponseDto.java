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
@Schema(description = "채팅방 목록 항목")
public class ChatRoomResponseDto {

    private final Long roomId;
    private final ChatRoomType type;
    private final String name;
    private final boolean active;
    @Schema(description = "연관 도메인 타입 (STORE, ORDER, NONE 등)")
    private final ChatRoomRefType refType;
    @Schema(description = "연관 도메인 ID (가게 단톡방이면 storeId)")
    private final Long refId;
    private final String lastMessagePreview;
    private final LocalDateTime lastMessageAt;
    private final long unreadCount;
    private final long participantCount;

    public static ChatRoomResponseDto of(
            ChatRoom room,
            String lastMessagePreview,
            long unreadCount,
            long participantCount
    ) {
        return ChatRoomResponseDto.builder()
                .roomId(room.getChatroomId())
                .type(room.getType())
                .name(room.getName())
                .active(room.isActive())
                .refType(room.getRefType())
                .refId(room.getRefId())
                .lastMessagePreview(lastMessagePreview)
                .lastMessageAt(room.getLastMessageAt())
                .unreadCount(unreadCount)
                .participantCount(participantCount)
                .build();
    }
}
