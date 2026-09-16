package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    // 본인 참여 여부 검증 (상태 무관 — 재입장 처리용)
    Optional<ChatParticipant> findByChatRoom_ChatroomIdAndAccount_AccountId(Long roomId, Long accountId);

    // 참여 여부 + 방 활성 여부 + 요청자 계정 상태를 한 번에 검증 (WebSocket SUBSCRIBE/SEND).
    // 메시지 1건마다 호출되므로 엔티티 대신 스칼라 projection만 조회한다.
    // 계정 상태를 같이 읽는 이유는 ChatAccessStatus 주석 참고 — 조인 하나로 별도 조회를 없앤다.
    @Query("""
        SELECT new com.eeum.eeum.domain.chat.repository.ChatAccessStatus(
                p.status, p.chatRoom.isActive, p.account.status)
        FROM ChatParticipant p
        WHERE p.chatRoom.chatroomId = :roomId
          AND p.account.accountId = :accountId
    """)
    Optional<ChatAccessStatus> findAccessStatus(
            @Param("roomId") Long roomId, @Param("accountId") Long accountId);

    // 채팅방 참여자 목록 (상태 무관)
    List<ChatParticipant> findAllByChatRoom_ChatroomId(Long roomId);

    // 특정 상태 참여자만 조회 (ACTIVE = 브로드캐스트/상세 대상)
    List<ChatParticipant> findAllByChatRoom_ChatroomIdAndStatus(Long roomId, ParticipantStatus status);

    // 사용자의 특정 상태 참여 목록
    List<ChatParticipant> findAllByAccount_AccountIdAndStatus(Long accountId, ParticipantStatus status);

    // 활성 방에 한정한 참여 목록 — 채팅 unread의 DB 기준값 계산용.
    // 종료된 방까지 세면 Redis 캐시가 축출/만료됐을 때 회수 불가능한 unread가 되살아난다.
    @Query("""
        SELECT p FROM ChatParticipant p
        JOIN FETCH p.chatRoom r
        WHERE p.account.accountId = :accountId
          AND p.status = :status
          AND r.isActive = true
    """)
    List<ChatParticipant> findActiveParticipationsInActiveRooms(
            @Param("accountId") Long accountId, @Param("status") ParticipantStatus status);

    // ACTIVE 참여자 수 (GROUP 전체 퇴장 판정)
    long countByChatRoom_ChatroomIdAndStatus(Long roomId, ParticipantStatus status);

    // ACTIVE 참여자 accountId 목록 (이벤트 리스너에서 LAZY 회피용)
    @Query("SELECT p.account.accountId FROM ChatParticipant p " +
            "WHERE p.chatRoom.chatroomId = :roomId AND p.status = 'ACTIVE'")
    List<Long> findActiveAccountIds(@Param("roomId") Long roomId);

    // lastReadTime 단조 증가 — 두 기기에서 동시에 읽음 처리할 때 늦게 커밋된 트랜잭션의
    // 더 과거 시각이 최신 시각을 덮어써 unread가 부풀어 오르는 것을 막는다.
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE ChatParticipant p
        SET p.lastReadTime = :readAt
        WHERE p.chatparticipantId = :participantId
          AND (p.lastReadTime IS NULL OR p.lastReadTime < :readAt)
    """)
    int advanceLastReadTime(
            @Param("participantId") Long participantId, @Param("readAt") LocalDateTime readAt);

    // 채팅방 목록 N+1 방지 — roomId별 참여자 수 배치 조회
    @Query("SELECT cp.chatRoom.chatroomId, COUNT(cp) FROM ChatParticipant cp " +
           "WHERE cp.chatRoom.chatroomId IN :roomIds AND cp.status = :status " +
           "GROUP BY cp.chatRoom.chatroomId")
    List<Object[]> countGroupedByRoomIdsAndStatus(
            @Param("roomIds") List<Long> roomIds, @Param("status") ParticipantStatus status);

    // 사장 통합 고객 목록 — 특정 상점 채팅방에 ACTIVE 참여 중인 고객 accountId 목록 (합집합 구성 및 참여 여부 판정용)
    @Query("""
        SELECT DISTINCT cp.account.accountId
        FROM ChatParticipant cp
        WHERE cp.chatRoom.refType = :refType
          AND cp.chatRoom.refId   = :storeId
          AND cp.status           = :status
    """)
    List<Long> findActiveParticipantAccountIdsByStoreRefId(
            @Param("storeId") Long storeId,
            @Param("refType") ChatRoomRefType refType,
            @Param("status") ParticipantStatus status
    );

}
