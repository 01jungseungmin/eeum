package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepositoryCustom {

    // account, category, region 일괄 fetch — LazyInitializationException 방지
    @EntityGraph(attributePaths = {"account", "category", "region"})
    @Query("SELECT p FROM CommunityPost p WHERE p.postId = :id AND p.hidden = false")
    Optional<CommunityPost> findById(@Param("id") Long id);

    // 신고 상세의 대상 스냅샷 — 작성자만 필요하므로 category/region까지 조인하지 않는다
    @EntityGraph(attributePaths = "account")
    Optional<CommunityPost> findWithAccountByPostId(Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CommunityPost p JOIN FETCH p.account WHERE p.postId = :postId")
    Optional<CommunityPost> findWithAccountByPostIdForUpdate(@Param("postId") Long postId);

    @EntityGraph(attributePaths = {"account", "category", "region"})
    Page<CommunityPost> findByAccount_AccountIdAndHiddenFalseOrderByCreatedAtDesc(
            Long accountId,
            Pageable pageable
    );

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
