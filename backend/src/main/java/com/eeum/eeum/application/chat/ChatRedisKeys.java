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

    // 멱등키에 반드시 계정/방 스코프를 넣는다.
    // clientMessageId만 쓰면 클라이언트가 UUID가 아닌 값(로컬 시퀀스 등)을 보낼 때
    // 서로 다른 사용자의 메시지가 서로를 차단해 메시지가 조용히 유실된다.
    public static String messageIdempotency(Long accountId, Long roomId, String clientMessageId) {
        return "chat:msg:idem:" + accountId + ":" + roomId + ":" + clientMessageId;
    }
}
