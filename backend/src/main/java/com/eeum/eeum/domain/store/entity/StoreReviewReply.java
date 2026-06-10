package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상점 리뷰 답글 — 사장 회원만 작성 가능, 리뷰당 1개
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "store_review_reply")
public class StoreReviewReply extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_reply_id")
    private Long storereplyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_review_id", nullable = false, unique = true)
    private StoreReview storeReview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // ===================== 정적 팩토리 메서드 =====================

    public static StoreReviewReply create(
            StoreReview storeReview,
            Account account,
            String content
    ) {
        StoreReviewReply reply = new StoreReviewReply();
        reply.storeReview = storeReview;
        reply.account = account;
        reply.content = content;
        return reply;
    }

    // ===================== 도메인 메서드 =====================

    public void update(String content) {
        this.content = content;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }
}