package com.eeum.eeum.application.community.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityCommentLike;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.entity.CommunityPostLike;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityLikeService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;

    @Transactional
    public void likePost(Long accountId, Long postId) {
        Account account = getAccountOrThrow(accountId);
        CommunityPost post = getPostOrThrow(postId);

        validateSameRegion(post, account);

        if (postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)) {
            throw new ConflictException(ErrorCode.COMMUNITY_POST_LIKE_ALREADY_EXISTS);
        }

        try {
            postLikeRepository.saveAndFlush(CommunityPostLike.create(account, post));
        } catch (DataIntegrityViolationException e) {

            log.error("게시글 좋아요 저장 실패: accountId={}, postId={}, cause={}",
                    accountId, postId, e.getMostSpecificCause().getMessage(), e);

            throw new ConflictException(ErrorCode.COMMUNITY_POST_LIKE_ALREADY_EXISTS);
        }

        post.increaseLikeCount();
    }

    @Transactional
    public void unlikePost(Long accountId, Long postId) {
        Account account = getAccountOrThrow(accountId);

        CommunityPostLike like = postLikeRepository
                .findByAccount_AccountIdAndPost_PostId(accountId, postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_LIKE_NOT_FOUND));

        CommunityPost post = like.getPost();
        validateSameRegion(post, account);

        postLikeRepository.delete(like);
        post.decreaseLikeCount();
    }

    @Transactional
    public void likeComment(Long accountId, Long commentId) {
        Account account = getAccountOrThrow(accountId);
        CommunityComment comment = getCommentOrThrow(commentId);

        if (comment.isDeleted()) {
            throw new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }

        validateSameRegion(comment.getPost(), account);

        if (commentLikeRepository.existsByAccount_AccountIdAndComment_CommentId(accountId, commentId)) {
            throw new ConflictException(ErrorCode.COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS);
        }

        try {
            commentLikeRepository.saveAndFlush(CommunityCommentLike.create(account, comment));
        } catch (DataIntegrityViolationException e) {
            log.error("댓글 좋아요 저장 실패: accountId={}, commentId={}, cause={}",
                    accountId, commentId, e.getMostSpecificCause().getMessage(), e);

            throw new ConflictException(ErrorCode.COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS);
        }

        comment.increaseLikeCount();
    }

    @Transactional
    public void unlikeComment(Long accountId, Long commentId) {
        Account account = getAccountOrThrow(accountId);

        CommunityCommentLike like = commentLikeRepository
                .findByAccount_AccountIdAndComment_CommentId(accountId, commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_LIKE_NOT_FOUND));

        CommunityComment comment = like.getComment();
        validateSameRegion(comment.getPost(), account);

        commentLikeRepository.delete(like);
        comment.decreaseLikeCount();
    }

    private Account getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private CommunityComment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
    }

    private Region getPrimaryRegion(Account account) {
        Long primaryAccountRegionId = account.getPrimaryRegionId();

        if (primaryAccountRegionId == null) throw new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        AccountRegion accountRegion = accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(
                        primaryAccountRegionId,
                        account.getAccountId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND));

        if (!accountRegion.isVerified()) {
            throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
        }

        return accountRegion.getRegion();
    }

    private void validateSameRegion(CommunityPost post, Account account) {
        Region myRegion = getPrimaryRegion(account);

        if (!post.getRegion().getRegionId().equals(myRegion.getRegionId())) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
    }
}