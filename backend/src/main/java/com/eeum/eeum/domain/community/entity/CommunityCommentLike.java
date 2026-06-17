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
@Table(name = "community_comment_like",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_community_comment_like_account_comment",
                        columnNames = {"account_id", "community_comment_id"}
                )
        }
)
public class CommunityCommentLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_comment_like_id")
    private Long commentLikeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_comment_id", nullable = false)
    private CommunityComment comment;

    public static CommunityCommentLike create(Account account, CommunityComment comment) {
        CommunityCommentLike like = new CommunityCommentLike();
        like.account = account;
        like.comment = comment;
        return like;
    }
}