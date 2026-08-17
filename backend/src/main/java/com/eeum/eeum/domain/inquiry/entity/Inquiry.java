package com.eeum.eeum.domain.inquiry.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inquiry")
public class Inquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account writer;

    // STORE 문의면 필수, ADMIN 문의면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private InquiryTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private InquiryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InquiryStatus status;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "secret", nullable = false)
    private boolean secret;

    // ===================== 정적 팩토리 메서드 =====================

    public static Inquiry create(
            Account writer,
            Store store,
            InquiryTargetType targetType,
            InquiryCategory category,
            String title,
            String content,
            boolean secret
    ) {
        Inquiry inquiry = new Inquiry();
        inquiry.writer = writer;
        inquiry.store = store;
        inquiry.targetType = targetType;
        inquiry.category = category;
        inquiry.status = InquiryStatus.PENDING;
        inquiry.title = title;
        inquiry.content = content;
        inquiry.secret = secret;
        return inquiry;
    }

    // ===================== 도메인 메서드 =====================

    public void markAnswered() {
        this.status = InquiryStatus.ANSWERED;
    }

    /**
     * 관리자가 문의를 강제 종료한다. 스팸이거나 답변이 불필요한 문의를 목록에서 걷어내는 용도다.
     * 종료된 문의는 {@link #isAnswerable()}이 false가 되어 답변이 달리지 않는다.
     */
    public void close() {
        this.status = InquiryStatus.CLOSED;
    }

    /**
     * 종료된 문의를 다시 연다. 재오픈은 "답변이 다시 필요하다"는 뜻이므로
     * 기존 답변 유무와 무관하게 PENDING으로 되돌린다 — 그래야 미답변 필터에 다시 잡힌다.
     */
    public void reopen() {
        this.status = InquiryStatus.PENDING;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.writer.getAccountId().equals(accountId);
    }

    public boolean isAnswerable() {
        return this.status == InquiryStatus.PENDING;
    }

    public boolean isClosed() {
        return this.status == InquiryStatus.CLOSED;
    }
}
