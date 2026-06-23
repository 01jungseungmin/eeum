package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepositoryCustom {

    // account, category, region 일괄 fetch — LazyInitializationException 방지
    @EntityGraph(attributePaths = {"account", "category", "region"})
    Optional<CommunityPost> findById(Long id);

    @EntityGraph(attributePaths = {"account", "category", "region"})
    Page<CommunityPost> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 동시 조회/좋아요/댓글 작성 시 lost update 방지 — DB 레벨 원자적 증감
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityPost p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void increaseViewCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityPost p SET p.likeCount = p.likeCount + 1 WHERE p.postId = :postId")
    void increaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE CommunityPost p
        SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END
        WHERE p.postId = :postId
        """)
    void decreaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityPost p SET p.commentCount = p.commentCount + 1 WHERE p.postId = :postId")
    void increaseCommentCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE CommunityPost p
        SET p.commentCount = CASE WHEN p.commentCount > 0 THEN p.commentCount - 1 ELSE 0 END
        WHERE p.postId = :postId
        """)
    void decreaseCommentCount(@Param("postId") Long postId);
}
