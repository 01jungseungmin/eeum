package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.domain.chat.entity.ChatMessage;

import java.util.List;

public interface ChatMessageRepositoryCustom {

    // 채팅방 목록 N+1 방지 — 각 방의 최신 메시지(max chatmessageId)를 한 번에 조회
    List<ChatMessage> findLatestMessagesForRooms(List<Long> roomIds);

    /**
     * 방 메시지 목록 — 최신순 커서 페이징.
     *
     * <p>정렬은 {@code sentAt desc, chatMessageId desc}로 고정한다. tie-break가 없으면
     * 같은 시각 메시지의 순서가 흔들리고, 커서가 그 경계를 끊지 못해 메시지가 누락된다.
     *
     * @param cursor 직전 페이지의 마지막 메시지. 첫 페이지면 null이다.
     */
    CursorSlice<ChatMessage> findRoomMessages(Long roomId, ChatMessageCursor cursor, int size);

    /**
     * 방의 삭제되지 않은 이미지 메시지 목록 — 최신순 커서 페이징.
     */
    CursorSlice<ChatMessage> findRoomImageMessages(Long roomId, ChatMessageCursor cursor, int size);
}
