package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long chatroomId;

    // 채팅방 생성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Account creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChatRoomType type;

    // Polymorphic 참조 타입 (TRADE / STORE / COMMUNITY 등) — FK 아님
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 20)
    private ChatRoomRefType refType;

    // 연관 도메인 ID (Polymorphic 참조)
    @Column(name = "ref_id")
    private Long refId;

    // 채팅방 이름 (GROUP 타입에서 사용)
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // 마지막 메시지 시각 (목록 정렬용)
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    // 지역 기반 공개 방 목록 필터링용 (GROUP/GROUP_STREET — nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    // ===================== 정적 팩토리 메서드 =====================

    public static ChatRoom createGroup(
            Account creator,
            ChatRoomType type,
            String name,
            ChatRoomRefType refType,
            Long refId,
            Region region
    ) {
        ChatRoom room = new ChatRoom();
        room.creator = creator;
        room.type = type;
        room.name = name;
        room.refType = refType;
        room.refId = refId;
        room.region = region;
        room.isActive = true;
        return room;
    }

    // ===================== 도메인 메서드 =====================

    // 마지막 메시지 시각 갱신
    public void updateLastMessageAt(LocalDateTime sentAt) {
        this.lastMessageAt = sentAt;
    }

    // 채팅방 비활성화 (GROUP 전체 퇴장 시)
    public void deactivate() {
        this.isActive = false;
    }

    public boolean isGroup() {
        return this.type == ChatRoomType.GROUP || this.type == ChatRoomType.GROUP_STREET;
    }
}
