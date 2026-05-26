package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.enums.StoreNoticeType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "store_notice")
public class StoreNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 30)
    private StoreNoticeType noticeType;

    public static StoreNotice create(
            Store store,
            String title,
            String content,
            StoreNoticeType noticeType,
            boolean isPinned
    ) {
        StoreNotice notice = new StoreNotice();
        notice.store = store;
        notice.title = title;
        notice.content = content;
        notice.noticeType = noticeType;
        notice.isPinned = isPinned;
        notice.isActive = true;
        return notice;
    }

    public void update(String title, String content, StoreNoticeType noticeType, boolean isPinned) {
        this.title = title;
        this.content = content;
        this.noticeType = noticeType;
        this.isPinned = isPinned;
    }

    public void deactivate() { this.isActive = false; }
}