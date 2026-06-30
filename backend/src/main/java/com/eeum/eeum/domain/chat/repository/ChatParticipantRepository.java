package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    // 본인 참여 여부 검증 (상태 무관 — 재입장 처리용)
    Optional<ChatParticipant> findByChatRoom_ChatroomIdAndAccount_AccountId(Long roomId, Long accountId);

    // 채팅방 참여자 목록 (상태 무관)
    List<ChatParticipant> findAllByChatRoom_ChatroomId(Long roomId);

    // 특정 상태 참여자만 조회 (ACTIVE = 브로드캐스트/상세 대상)
    List<ChatParticipant> findAllByChatRoom_ChatroomIdAndStatus(Long roomId, ParticipantStatus status);

    // 사용자의 특정 상태 참여 목록
    List<ChatParticipant> findAllByAccount_AccountIdAndStatus(Long accountId, ParticipantStatus status);

    // ACTIVE 참여자 수 (GROUP 전체 퇴장 판정)
    long countByChatRoom_ChatroomIdAndStatus(Long roomId, ParticipantStatus status);

    // ACTIVE 참여자 accountId 목록 (이벤트 리스너에서 LAZY 회피용)
    @Query("SELECT p.account.accountId FROM ChatParticipant p " +
            "WHERE p.chatRoom.chatroomId = :roomId AND p.status = 'ACTIVE'")
    List<Long> findActiveAccountIds(@Param("roomId") Long roomId);

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
