package com.eeum.eeum.application.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatTypingResponseDto {

    private final Long roomId;
    private final Long accountId;
    private final String nickname;
    private final boolean typing;
}
