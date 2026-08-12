package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    // 신고 상세의 대상 스냅샷 — 작성자/게시글을 함께 조회해 N+1 방지
    @EntityGraph(attributePaths = {"account", "post"})
    Optional<CommunityComment> findWithAccountAndPostByCommentId(Long commentId);

    @Query("SELECT c.post.postId FROM CommunityComment c WHERE c.commentId = :commentId")
    Optional<Long> findPostIdByCommentId(@Param("commentId") Long commentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CommunityComment c WHERE c.commentId = :commentId")
    Optional<CommunityComment> findWithAccountByCommentIdForUpdate(@Param("commentId") Long commentId);

    @EntityGraph(attributePaths = "account")
    Page<CommunityComment> findByPost_PostIdAndParentCommentIsNull(Long postId, Pageable pageable);

    @EntityGraph(attributePaths = "account")
    Page<CommunityComment> findByParentComment_CommentId(Long parentCommentId, Pageable pageable);

    // 내가 작성한 댓글/대댓글 목록 (마이페이지용) — 삭제된 댓글 제외
    @EntityGraph(attributePaths = "account")
    Page<CommunityComment> findByAccount_AccountIdAndDeletedFalseOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 좋아요 수 원자적 증감 — 엔티티 메모리 증감(read-modify-write)은 동시 요청 시 lost update가 발생하므로
    // 게시글(CommunityPostRepository)과 동일하게 DB UPDATE로 처리한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityComment c SET c.likeCount = c.likeCount + 1 WHERE c.commentId = :commentId")
    void increaseLikeCount(@Param("commentId") Long commentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE CommunityComment c
        SET c.likeCount = CASE WHEN c.likeCount > 0 THEN c.likeCount - 1 ELSE 0 END
        WHERE c.commentId = :commentId
    """)
    void decreaseLikeCount(@Param("commentId") Long commentId);

    // 게시글 hard-delete 시 대댓글 우선 삭제 — parent_comment_id 자기참조 FK 위반 방지
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommunityComment c WHERE c.post.postId = :postId AND c.parentComment IS NOT NULL")
    void deleteRepliesByPost_PostId(@Param("postId") Long postId);

    // 게시글 hard-delete 시 부모 댓글 삭제 — 대댓글 삭제 이후 호출해야 함
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommunityComment c WHERE c.post.postId = :postId AND c.parentComment IS NULL")
    void deleteTopLevelCommentsByPost_PostId(@Param("postId") Long postId);
}
