package com.eeum.eeum.domain.chat.event;

// 채팅방 읽음 처리 완료 시 발행 — AFTER_COMMIT 후 Redis unread 리셋
public record ChatRoomReadEvent(
        Long accountId,
        Long roomId
) {
}
