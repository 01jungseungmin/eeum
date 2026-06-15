package com.eeum.eeum.application.chat.helper;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.enums.MessageType;

public final class ChatMessagePreview {

    private static final int MAX_LENGTH = 50;

    private ChatMessagePreview() {}

    public static String of(ChatMessage message) {
        if (message == null) return null;
        if (message.isDeleted()) return "삭제된 메시지입니다.";
        if (message.getMessageType() == MessageType.IMAGE) return "사진을 보냈습니다.";
        String content = message.getContent();
        if (content == null) return null;
        return content.length() > MAX_LENGTH ? content.substring(0, MAX_LENGTH) + "…" : content;
    }
}
