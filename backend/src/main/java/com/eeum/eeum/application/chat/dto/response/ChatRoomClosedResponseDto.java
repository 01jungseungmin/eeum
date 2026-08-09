package com.eeum.eeum.application.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅방 종료 알림 (WebSocket /sub/chat/rooms/{roomId}/closed)")
public class ChatRoomClosedResponseDto {

    @Schema(description = "종료된 채팅방 ID")
    private final Long roomId;

    @Schema(description = "종료를 수행한 accountId (관리자 강제 종료 시 null)")
    private final Long closedBy;

    @Schema(description = "DB에 기록된 실제 종료 시각")
    private final LocalDateTime closedAt;

    public static ChatRoomClosedResponseDto of(Long roomId, Long closedBy, LocalDateTime closedAt) {
        return ChatRoomClosedResponseDto.builder()
                .roomId(roomId)
                .closedBy(closedBy)
                .closedAt(closedAt)
                .build();
    }
}
