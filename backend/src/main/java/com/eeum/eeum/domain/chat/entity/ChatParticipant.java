package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_participant",
        uniqueConstraints = {
                // 참여자 등록은 "조회 후 없으면 INSERT"라 Redis 락이 만료되면 중복 행이 생긴다.
                // 중복이 생기면 findByChatRoom_ChatroomIdAndAccount_AccountId(단건 Optional)가
                // IncorrectResultSizeDataAccessException을 던져 그 방의 모든 요청이 500이 된다.
                //
                // 이름은 init.sql의 기존 제약(uk_chat_participant)과 반드시 일치시킨다.
                // 다른 이름을 쓰면 init.sql로 만든 DB에 동일 컬럼 유니크 인덱스가 2개 생겨
                // INSERT마다 불필요한 인덱스 유지 비용이 든다. 엔티티에서 제거하지는 않는다 —
                // 테스트처럼 init.sql 없이 엔티티만으로 스키마를 만드는 환경에서는 이 선언이 유일한 근거다.
                //
                // [운영 주의] 이미 중복 행이 있으면 인덱스 생성이 실패하고 ddl-auto=update는 로그만 남긴다.
                //   SELECT chat_room_id, account_id, COUNT(*) FROM chat_participant
                //   GROUP BY chat_room_id, account_id HAVING COUNT(*) > 1;
                @UniqueConstraint(
                        name = "uk_chat_participant",
                        columnNames = {"chat_room_id", "account_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_participant_id")
    private Long chatparticipantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // 마지막 읽은 시각 (안 읽은 메시지 수 계산)
    @Column(name = "last_read_time")
    private LocalDateTime lastReadTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParticipantStatus status = ParticipantStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // ===================== 정적 팩토리 메서드 =====================

    public static ChatParticipant create(ChatRoom chatRoom, Account account) {
        ChatParticipant participant = new ChatParticipant();
        participant.chatRoom = chatRoom;
        participant.account = account;
        participant.status = ParticipantStatus.ACTIVE;
        participant.joinedAt = LocalDateTime.now();
        participant.lastReadTime = participant.joinedAt;
        return participant;
    }

    // ===================== 도메인 메서드 =====================

    // 퇴장 처리 (status=LEFT, leftAt 기록)
    public void leave() {
        this.status = ParticipantStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    // 재입장 (LEFT 상태였던 참여자를 다시 ACTIVE로)
    public void rejoin() {
        this.status = ParticipantStatus.ACTIVE;
        this.leftAt = null;
        this.joinedAt = LocalDateTime.now();
        this.lastReadTime = this.joinedAt;
    }

    public void updateLastReadTime(LocalDateTime time) {
        this.lastReadTime = time;
    }

    public boolean isActive() {
        return this.status == ParticipantStatus.ACTIVE;
    }

    public LocalDateTime unreadSince() {
        return lastReadTime != null ? lastReadTime : joinedAt;
    }
}
