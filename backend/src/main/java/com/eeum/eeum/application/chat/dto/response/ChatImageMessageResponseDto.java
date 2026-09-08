package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅 사진 모아보기 항목")
public class ChatImageMessageResponseDto {

    @Schema(description = "메시지 ID")
    private final Long messageId;

    @Schema(description = "발신자 계정 ID")
    private final Long senderAccountId;

    @Schema(description = "발신자 표시명")
    private final String senderName;

    @Schema(description = "이미지 조회 URL")
    private final String imageUrl;

    @Schema(description = "전송 시각")
    private final LocalDateTime sentAt;

    public static ChatImageMessageResponseDto from(ChatMessage message) {
        return ChatImageMessageResponseDto.builder()
                .messageId(message.getChatmessageId())
                .senderAccountId(message.getAccount().getAccountId())
                .senderName(message.getAccount().getDisplayName())
                .imageUrl(message.getImageUrl())
                .sentAt(message.getSentAt())
                .build();
    }
}
