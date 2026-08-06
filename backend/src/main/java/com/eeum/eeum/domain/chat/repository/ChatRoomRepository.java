package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {

    // 가게 단톡방 멱등 검증 / 상점 상세·대시보드 노출 공용 — 반드시 ACTIVE 방만 조회한다.
    // 종료(isActive=false)된 방까지 조회하면 (1) 신규 생성이 차단되고 (2) 사용자가 종료된 옛 방으로 유입된다.
    // 동일 조합의 종료된 방이 여러 건 누적될 수 있으므로 단건 Optional 대신 최신 1건(findFirst + 정렬)을 취한다 —
    // 정렬 없는 단건 조회는 2건 이상일 때 IncorrectResultSizeDataAccessException으로 500을 유발한다.
    // type을 조건에 넣지 않는다 — 조회 기준과 유니크 제약(uk_chat_room_active_ref)을 일치시켜야
    // 생성 시 멱등 반환하는 방과 상점 화면이 노출하는 방이 갈리지 않는다.
    Optional<ChatRoom> findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
            ChatRoomRefType refType, Long refId);

    // 메시지 저장 / 입장 / 초대 / 퇴장 / 종료가 공유하는 DB 최종 방어선.
    // Redis 락의 lease가 만료되어 다른 요청이 진입해도 같은 방 행에서는 커밋까지 직렬화된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ChatRoom r WHERE r.chatroomId = :roomId")
    Optional<ChatRoom> findByIdWithPessimisticLock(@Param("roomId") Long roomId);

    // 주어진 방 중 종료된 것만 — Redis에 남은 종료 방 unread 키 정리용
    @Query("SELECT r.chatroomId FROM ChatRoom r WHERE r.chatroomId IN :roomIds AND r.isActive = false")
    List<Long> findClosedRoomIdsIn(@Param("roomIds") List<Long> roomIds);

    // 종료 전이를 조건부 UPDATE로 수행하고 영향 행 수를 돌려준다.
    // 엔티티 스냅샷으로 isActive를 판정하면 (1) OSIV로 1차 캐시가 재사용돼 락 획득 이전 상태를 보고
    // (2) 그 사이 다른 경로가 이미 종료한 방을 다시 종료해 closedAt이 덮어써지고 종료 이벤트가 중복 발행된다.
    // 여기서 1을 받은 호출자만 "이번에 종료시킨 주체"이므로 이벤트/시스템 메시지를 발행한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ChatRoom r
        SET r.isActive = false, r.closedAt = :closedAt
        WHERE r.chatroomId = :roomId AND r.isActive = true
    """)
    int closeIfActive(@Param("roomId") Long roomId, @Param("closedAt") LocalDateTime closedAt);
}
