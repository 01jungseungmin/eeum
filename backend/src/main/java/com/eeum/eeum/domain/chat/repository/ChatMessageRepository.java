package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, ChatMessageRepositoryCustom {

    // 본인 메시지 검증용
    Optional<ChatMessage> findByChatmessageIdAndAccount_AccountId(Long messageId, Long accountId);

    // 관리자 대화 열람(번호 페이징) 전용. 사용자 목록은 findRoomMessages(커서)를 쓴다 —
    // 시각 단독 정렬은 같은 시각 메시지의 순서를 보장하지 못한다.
    Page<ChatMessage> findAllByChatRoom_ChatroomIdOrderBySentAtDesc(Long roomId, Pageable pageable);

    // 마지막 메시지 (목록 화면 미리보기 / 읽음 처리 시점 최신 messageId)
    Optional<ChatMessage> findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(Long roomId);

    long countByChatRoom_ChatroomIdAndSentAtAfterAndAccount_AccountIdNot(Long roomId, LocalDateTime lastReadTime, Long accountId);

    // 중고 문의방의 "첫 문의" 판정용. 방 생성 시 시스템 메시지를 남기지 않으므로
    // 이 값이 1이면 방금 저장된 그 메시지가 방의 첫 메시지다.
    long countByChatRoom_ChatroomId(Long roomId);
}
