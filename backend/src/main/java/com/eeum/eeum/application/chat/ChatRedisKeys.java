package com.eeum.eeum.application.chat;

public final class ChatRedisKeys {

    private ChatRedisKeys() {
    }

    public static String totalUnread(Long accountId) {
        return "unread:chat:" + accountId;
    }

    public static String roomUnread(Long accountId, Long roomId) {
        return "unread:chat:" + accountId + ":room:" + roomId;
    }

    public static String messageIdempotency(String clientMessageId) {
        return "chat:msg:idem:" + clientMessageId;
    }
}
