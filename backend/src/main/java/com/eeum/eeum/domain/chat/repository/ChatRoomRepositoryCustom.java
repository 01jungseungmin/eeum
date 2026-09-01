package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.eeum.eeum.common.dto.response.CursorSlice;

public interface ChatRoomRepositoryCustom {

    // 내 채팅방 목록 (ACTIVE 참여자 기준, lastMessageAt 내림차순) — 무한 스크롤
    // includeClosed=true면 종료된 방까지 포함 (지난 대화 열람용). 기본은 활성 방만.
    /**
     * @param cursor 직전 페이지의 마지막 방. 첫 페이지면 null이다.
     * @param size   한 페이지 크기. 다음 페이지 여부 판정을 위해 내부적으로 한 건 더 읽는다.
     */
    CursorSlice<ChatRoom> findMyRooms(Long accountId, ChatRoomCursor cursor, int size, boolean includeClosed);

    // 지역 내 공개 채팅방 목록 (GROUP/GROUP_STREET, isActive=true) — 무한 스크롤
    CursorSlice<ChatRoom> findPublicRooms(Long regionId, ChatRoomCursor cursor, int size);

    // 관리자용 채팅방 검색 (타입/활성여부/기간 필터)
    Page<ChatRoom> searchRoomsByAdmin(ChatRoomAdminSearchDto condition, Pageable pageable);
}
