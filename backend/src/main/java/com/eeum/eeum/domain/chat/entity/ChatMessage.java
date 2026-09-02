package com.eeum.eeum.domain.chat.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.enums.MessageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_message",
        indexes = {
                // 방 메시지 목록 — 방으로 좁히고 최신순으로 읽는다. 커서 조건과 정렬이
                // 모두 이 인덱스로 풀린다. PK를 명시해야 같은 sent_at 구간의 정렬까지 인덱스가 맡는다.
                @Index(name = "idx_chat_message_room_sent",
                        columnList = "chat_room_id, sent_at, chat_message_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long chatmessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 발신자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // 메시지 내용 (TEXT/SYSTEM 타입)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // 이미지 URL (IMAGE 타입)
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    // 발신자 본인 삭제 시각 (Soft Delete — null이면 미삭제)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 발신 시각 (인덱스 대상)
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    // ===================== 정적 팩토리 메서드 =====================

    public static ChatMessage text(ChatRoom chatRoom, Account sender, String content) {
        return create(chatRoom, sender, content, null, MessageType.TEXT);
    }

    public static ChatMessage image(ChatRoom chatRoom, Account sender, String imageUrl) {
        return create(chatRoom, sender, null, imageUrl, MessageType.IMAGE);
    }

    public static ChatMessage system(ChatRoom chatRoom, Account actor, String content) {
        return create(chatRoom, actor, content, null, MessageType.SYSTEM);
    }

    private static ChatMessage create(
            ChatRoom chatRoom,
            Account account,
            String content,
            String imageUrl,
            MessageType messageType
    ) {
        ChatMessage message = new ChatMessage();
        message.chatRoom = chatRoom;
        message.account = account;
        message.content = content;
        message.imageUrl = imageUrl;
        message.messageType = messageType;
        message.sentAt = LocalDateTime.now();
        return message;
    }

    // ===================== 도메인 메서드 =====================

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Soft Delete — 삭제 시각 기록
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }

    // 발신자 본인 여부
    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }

    // 본인 발신 + 미삭제 + 시스템 메시지가 아닐 때만 삭제 가능
    public boolean isDeletable(Long accountId) {
        return isOwnedBy(accountId) && deletedAt == null && this.messageType != MessageType.SYSTEM;
    }
}
