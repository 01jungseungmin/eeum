package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatMessage;

import java.util.List;

public interface ChatMessageRepositoryCustom {

    // 채팅방 목록 N+1 방지 — 각 방의 최신 메시지(max chatmessageId)를 한 번에 조회
    List<ChatMessage> findLatestMessagesForRooms(List<Long> roomIds);
}
