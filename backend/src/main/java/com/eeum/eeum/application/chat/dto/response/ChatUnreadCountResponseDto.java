package com.eeum.eeum.application.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "전체 안 읽은 채팅 메시지 수")
public class ChatUnreadCountResponseDto {

    @Schema(description = "안 읽은 채팅 메시지 총합", example = "5")
    private final long unreadCount;

    public static ChatUnreadCountResponseDto of(long unreadCount) {
        return ChatUnreadCountResponseDto.builder()
                .unreadCount(unreadCount)
                .build();
    }
}
