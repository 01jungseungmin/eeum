package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ChatRoomRepositoryCustom {

    // 내 채팅방 목록 (ACTIVE 참여자 기준, lastMessageAt 내림차순) — 무한 스크롤
    Slice<ChatRoom> findMyRooms(Long accountId, Pageable pageable);

    // 관리자용 채팅방 검색 (타입/활성여부/기간 필터)
    Page<ChatRoom> searchRoomsByAdmin(ChatRoomAdminSearchDto condition, Pageable pageable);
}
