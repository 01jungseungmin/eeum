package com.eeum.eeum.application.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatReadResponseDto {

    private final Long roomId;
    private final Long accountId;
    private final Long lastReadMessageId;
    private final LocalDateTime readAt;
}
