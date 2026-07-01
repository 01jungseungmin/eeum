package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {

    // 가게 단톡방 멱등 검증 / 1:1 채팅방 조회용
    Optional<ChatRoom> findByRefTypeAndRefIdAndType(
            ChatRoomRefType refType, Long refId, ChatRoomType type);

    Optional<ChatRoom> findByRefTypeAndRefId(ChatRoomRefType refType, Long refId);
}
