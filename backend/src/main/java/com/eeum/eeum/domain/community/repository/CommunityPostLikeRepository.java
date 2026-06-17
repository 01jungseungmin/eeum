package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {

    Optional<CommunityPostLike> findByAccount_AccountIdAndPost_PostId(Long accountId, Long postId);

    boolean existsByAccount_AccountIdAndPost_PostId(Long accountId, Long postId);

    @Query("""
       SELECT pl.post.postId
       FROM CommunityPostLike pl
       WHERE pl.account.accountId = :accountId
       AND pl.post.postId IN :postIds
       """)
    Set<Long> findLikedPostIds(
            @Param("accountId") Long accountId,
            @Param("postIds") List<Long> postIds
    );

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CommunityPostLike pl WHERE pl.post.postId = :postId")
    void deleteByPost_PostId(@Param("postId") Long postId);
}
