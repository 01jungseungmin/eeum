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

    // 댓글 soft-delete 시 대댓글 일괄 처리 — 반환값은 처리된 대댓글 수
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CommunityComment c SET c.deleted = true, c.content = '삭제된 댓글입니다.' WHERE c.parentComment.commentId = :parentCommentId AND c.deleted = false")
    int softDeleteRepliesByParentId(@Param("parentCommentId") Long parentCommentId);

    // 게시글 hard-delete 시 소속 댓글 전체 삭제
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CommunityComment c WHERE c.post.postId = :postId")
    void deleteByPost_PostId(@Param("postId") Long postId);
}
