package com.eeum.eeum.application.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "타이핑 인디케이터 요청 (WebSocket /pub/chat/rooms/{roomId}/typing)")
@Getter
@NoArgsConstructor
public class ChatTypingRequestDto {

    @Schema(description = "타이핑 중 여부 (true: 입력 중, false: 입력 중단)", example = "true")
    private boolean typing;
}
