package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ChatRoomRepositoryCustom {

    // 내 채팅방 목록 (ACTIVE 참여자 기준, lastMessageAt 내림차순) — 무한 스크롤
    // includeClosed=true면 종료된 방까지 포함 (지난 대화 열람용). 기본은 활성 방만.
    Slice<ChatRoom> findMyRooms(Long accountId, Pageable pageable, boolean includeClosed);

    // 지역 내 공개 채팅방 목록 (GROUP/GROUP_STREET, isActive=true) — 무한 스크롤
    Slice<ChatRoom> findPublicRooms(Long regionId, Pageable pageable);

    // 관리자용 채팅방 검색 (타입/활성여부/기간 필터)
    Page<ChatRoom> searchRoomsByAdmin(ChatRoomAdminSearchDto condition, Pageable pageable);
}
