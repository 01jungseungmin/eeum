package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅 메시지")
public class ChatMessageResponseDto {

    private static final String DELETED_PLACEHOLDER = "삭제된 메시지입니다";

    private final Long messageId;
    private final Long roomId;
    private final Long senderAccountId;
    private final String senderName;
    private final String senderProfileImageUrl;
    private final String content;
    private final String imageUrl;
    private final MessageType messageType;
    private final boolean deleted;
    private final LocalDateTime sentAt;

    public static ChatMessageResponseDto from(ChatMessage message) {
        Account sender = message.getAccount();
        boolean deleted = message.isDeleted();
        return ChatMessageResponseDto.builder()
                .messageId(message.getChatmessageId())
                .roomId(message.getChatRoom().getChatroomId())
                .senderAccountId(sender.getAccountId())
                .senderName(sender.getName())
                .senderProfileImageUrl(sender.getProfileImageUrl())
                .content(deleted ? DELETED_PLACEHOLDER : message.getContent())
                .imageUrl(deleted ? null : message.getImageUrl())
                .messageType(message.getMessageType())
                .deleted(deleted)
                .sentAt(message.getSentAt())
                .build();
    }
}
