package com.eeum.eeum.domain.community.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_comment")
public class CommunityComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_comment_id")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // null이면 댓글, 값이 있으면 대댓글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private CommunityComment parentComment;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public static CommunityComment createComment(CommunityPost post, Account account, String content) {
        CommunityComment comment = new CommunityComment();
        comment.post = post;
        comment.account = account;
        comment.content = content;
        return comment;
    }

    public static CommunityComment createReply(CommunityPost post, Account account, CommunityComment parentComment, String content) {
        CommunityComment reply = new CommunityComment();
        reply.post = post;
        reply.account = account;
        reply.parentComment = parentComment;
        reply.content = content;
        return reply;
    }

    public void update(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deleted = true;
        this.content = "삭제된 댓글입니다.";
    }

    public boolean isReply() {
        return this.parentComment != null;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.account.getAccountId().equals(accountId);
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
