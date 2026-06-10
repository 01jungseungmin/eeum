package com.eeum.eeum.domain.notification.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 40)
    private NotificationRefType refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // ===================== 정적 팩토리 메서드 =====================

    public static Notification create(
            Account account,
            NotificationType type,
            String title,
            String content,
            NotificationRefType refType,
            Long refId,
            String linkUrl
    ) {
        Notification notification = new Notification();
        notification.account  = account;
        notification.type     = type;
        notification.title    = title;
        notification.content  = content;
        notification.refType  = refType;
        notification.refId    = refId;
        notification.linkUrl  = linkUrl;
        notification.isRead   = false;
        return notification;
    }

    // ===================== 도메인 메서드 =====================

    // 읽음 처리 (isRead=true, readAt 기록)
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    // 본인 알림 여부
    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }

    // 안 읽음 여부
    public boolean isUnread() {
        return !this.isRead;
    }
}
