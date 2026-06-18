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
@Table(name = "community_post_like",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_community_post_like_account_post",
                        columnNames = {"account_id", "community_post_id"}
                )
        }
)
public class CommunityPostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_like_id")
    private Long postLikeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost post;

    public static CommunityPostLike create(Account account, CommunityPost post) {
        CommunityPostLike like = new CommunityPostLike();
        like.account = account;
        like.post = post;
        return like;
    }
}