package com.eeum.eeum.application.community.service;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CommunityPostDeletionProcessor {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityImageRepository imageRepository;

    /**
     * 호출자는 반드시 동일 트랜잭션에서 대상 CommunityPost 행을 먼저 비관적 잠금해야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteLockedPost(CommunityPost post) {
        Long postId = post.getPostId();

        commentLikeRepository.deleteByComment_Post_PostId(postId);
        commentRepository.deleteRepliesByPost_PostId(postId);
        commentRepository.deleteTopLevelCommentsByPost_PostId(postId);
        postLikeRepository.deleteByPost_PostId(postId);
        imageRepository.deleteByPost_PostId(postId);
        postRepository.delete(post);
    }
}
