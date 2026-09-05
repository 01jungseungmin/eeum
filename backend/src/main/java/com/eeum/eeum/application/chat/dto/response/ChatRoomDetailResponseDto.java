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

    @Schema(description = "중고 문의방이면 대상 게시글 요약. 그 외 방에서는 null")
    private final UsedProductChatSummaryDto usedProduct;

    public static ChatRoomDetailResponseDto of(
            ChatRoom room,
            List<ChatParticipantResponseDto> participants
    ) {
        return of(room, participants, null);
    }

    public static ChatRoomDetailResponseDto of(
            ChatRoom room,
            List<ChatParticipantResponseDto> participants,
            UsedProductChatSummaryDto usedProduct
    ) {
        return ChatRoomDetailResponseDto.builder()
                .usedProduct(usedProduct)
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
