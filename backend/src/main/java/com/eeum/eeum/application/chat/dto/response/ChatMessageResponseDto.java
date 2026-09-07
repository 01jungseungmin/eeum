package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.function.Function;

@Getter
@Builder
@Schema(description = "채팅 메시지")
public class ChatMessageResponseDto {

    private static final String DELETED_PLACEHOLDER = "삭제된 메시지입니다.";

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

    /**
     * @param imageUrlResolver objectKey를 조회용 URL로 바꾼다. DB에는 key만 있으므로 이 변환을
     *                         거치지 않은 값은 private 버킷에서 열리지 않는다.
     */
    public static ChatMessageResponseDto from(ChatMessage message, Function<String, String> imageUrlResolver) {
        Account sender = message.getAccount();
        boolean deleted = message.isDeleted();
        return ChatMessageResponseDto.builder()
                .messageId(message.getChatmessageId())
                .roomId(message.getChatRoom().getChatroomId())
                .senderAccountId(sender.getAccountId())
                // 실명이 아니라 표시명이다 — 문의방은 모르는 사람과 연결된다
                .senderName(sender.getDisplayName())
                .senderProfileImageUrl(imageUrlResolver.apply(sender.getProfileImageUrl()))
                .content(deleted ? DELETED_PLACEHOLDER : message.getContent())
                .imageUrl(deleted ? null : imageUrlResolver.apply(message.getImageUrl()))
                .messageType(message.getMessageType())
                .deleted(deleted)
                .sentAt(message.getSentAt())
                .build();
    }
}
