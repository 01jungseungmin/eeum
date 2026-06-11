package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 본인 메시지 검증용
    Optional<ChatMessage> findByChatmessageIdAndAccount_AccountId(Long messageId, Long accountId);

    // 채팅방 메시지 목록 (최신순)
    Page<ChatMessage> findAllByChatRoom_ChatroomIdOrderBySentAtDesc(Long roomId, Pageable pageable);

    // 커서 페이징 (과거 메시지 로드)
    List<ChatMessage> findAllByChatRoom_ChatroomIdAndSentAtBeforeOrderBySentAtDesc(
            Long roomId, LocalDateTime cursor, Pageable pageable);

    // 채팅방별 안 읽은 메시지 수 (lastReadTime 이후)
    long countByChatRoom_ChatroomIdAndSentAtAfter(Long roomId, LocalDateTime lastReadTime);

    // 마지막 메시지 (목록 화면 미리보기)
    Optional<ChatMessage> findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(Long roomId);

    // 채팅방 목록 N+1 방지 — 여러 채팅방의 최신 메시지를 한 번에 조회
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.chatRoom.chatroomId IN :roomIds " +
           "AND cm.sentAt = (SELECT MAX(m.sentAt) FROM ChatMessage m " +
           "WHERE m.chatRoom.chatroomId = cm.chatRoom.chatroomId)")
    List<ChatMessage> findLatestMessagesForRooms(@Param("roomIds") List<Long> roomIds);

    // 채팅방 강제 삭제 시 CASCADE
    void deleteAllByChatRoom_ChatroomId(Long roomId);
}
