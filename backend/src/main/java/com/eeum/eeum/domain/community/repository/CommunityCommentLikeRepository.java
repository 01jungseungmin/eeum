package com.eeum.eeum.domain.community.repository;

import com.eeum.eeum.domain.community.entity.CommunityCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CommunityCommentLikeRepository extends JpaRepository<CommunityCommentLike, Long> {

    Optional<CommunityCommentLike> findByAccount_AccountIdAndComment_CommentId(Long accountId, Long commentId);

    boolean existsByAccount_AccountIdAndComment_CommentId(Long accountId, Long commentId);

    @Query("SELECT cl.comment.commentId FROM CommunityCommentLike cl WHERE cl.account.accountId = :accountId AND cl.comment.commentId IN :commentIds")
    Set<Long> findLikedCommentIds(@Param("accountId") Long accountId, @Param("commentIds") List<Long> commentIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CommunityCommentLike cl WHERE cl.comment.commentId = :commentId")
    void deleteByComment_CommentId(@Param("commentId") Long commentId);

    // 부모 댓글 삭제 시 대댓글 좋아요 일괄 삭제
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CommunityCommentLike cl WHERE cl.comment.parentComment.commentId = :parentCommentId")
    void deleteByComment_ParentComment_CommentId(@Param("parentCommentId") Long parentCommentId);

    // 게시글 삭제 시 모든 댓글 좋아요 일괄 삭제
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CommunityCommentLike cl WHERE cl.comment.post.postId = :postId")
    void deleteByComment_Post_PostId(@Param("postId") Long postId);
}
