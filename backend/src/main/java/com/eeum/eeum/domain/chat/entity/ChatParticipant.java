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
@Table(name = "chat_participant")
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
