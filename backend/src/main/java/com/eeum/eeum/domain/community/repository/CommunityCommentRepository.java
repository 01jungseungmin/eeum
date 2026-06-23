package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    @EntityGraph(attributePaths = "account")
    Page<CommunityComment> findByPost_PostIdAndParentCommentIsNull(Long postId, Pageable pageable);

    @EntityGraph(attributePaths = "account")
    Page<CommunityComment> findByParentComment_CommentId(Long parentCommentId, Pageable pageable);

    // 내가 작성한 댓글/대댓글 목록 (마이페이지용) — 삭제된 댓글 제외
    @EntityGraph(attributePaths = "account")
    Page<CommunityComment> findByAccount_AccountIdAndDeletedFalseOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 게시글 hard-delete 시 대댓글 우선 삭제 — parent_comment_id 자기참조 FK 위반 방지
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommunityComment c WHERE c.post.postId = :postId AND c.parentComment IS NOT NULL")
    void deleteRepliesByPost_PostId(@Param("postId") Long postId);

    // 게시글 hard-delete 시 부모 댓글 삭제 — 대댓글 삭제 이후 호출해야 함
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommunityComment c WHERE c.post.postId = :postId AND c.parentComment IS NULL")
    void deleteTopLevelCommentsByPost_PostId(@Param("postId") Long postId);
}
