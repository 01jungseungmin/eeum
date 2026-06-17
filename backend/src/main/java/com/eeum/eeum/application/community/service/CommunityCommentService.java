package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.request.CommunityCommentCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityCommentUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityCommentResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityCommentService {

    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityPostRepository postRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Page<CommunityCommentResponseDto> getComments(Long accountId, Long postId, Pageable pageable) {
        Account account = getAccountOrThrow(accountId);
        CommunityPost post = getPostOrThrow(postId);

        validateSameRegion(post, account);

        Page<CommunityComment> comments = commentRepository
                .findByPost_PostIdAndParentCommentIsNull(postId, pageable);

        Set<Long> likedIds = batchFetchLikedIds(accountId, comments.getContent());

        return comments.map(comment ->
                CommunityCommentResponseDto.of(
                        comment,
                        likedIds.contains(comment.getCommentId())
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<CommunityCommentResponseDto> getReplies(Long accountId, Long commentId, Pageable pageable) {
        Account account = getAccountOrThrow(accountId);
        CommunityComment parent = getCommentOrThrow(commentId);

        if (parent.isReply()) {
            throw new BadRequestException(ErrorCode.COMMUNITY_REPLY_DEPTH_EXCEEDED);
        }

        validateSameRegion(parent.getPost(), account);

        Page<CommunityComment> replies = commentRepository
                .findByParentComment_CommentId(commentId, pageable);

        Set<Long> likedIds = batchFetchLikedIds(accountId, replies.getContent());

        return replies.map(reply ->
                CommunityCommentResponseDto.of(
                        reply,
                        likedIds.contains(reply.getCommentId())
                )
        );
    }

    @Transactional
    public CommunityCommentResponseDto createComment(
            Long accountId,
            Long postId,
            CommunityCommentCreateRequestDto request
    ) {
        Account account = getAccountOrThrow(accountId);
        CommunityPost post = getPostOrThrow(postId);

        validateSameRegion(post, account);

        CommunityComment comment = CommunityComment.createComment(
                post,
                account,
                request.getContent()
        );

        commentRepository.save(comment);
        post.increaseCommentCount();

        log.info("댓글 작성: accountId={}, postId={}, commentId={}",
                accountId, postId, comment.getCommentId());

        return CommunityCommentResponseDto.of(comment, false);
    }

    @Transactional
    public CommunityCommentResponseDto createReply(
            Long accountId,
            Long parentCommentId,
            CommunityCommentCreateRequestDto request
    ) {
        Account account = getAccountOrThrow(accountId);
        CommunityComment parent = getCommentOrThrow(parentCommentId);

        if (parent.isReply()) {
            throw new BadRequestException(ErrorCode.COMMUNITY_REPLY_DEPTH_EXCEEDED);
        }

        validateSameRegion(parent.getPost(), account);

        CommunityComment reply = CommunityComment.createReply(
                parent.getPost(),
                account,
                parent,
                request.getContent()
        );

        commentRepository.save(reply);
        parent.getPost().increaseCommentCount();

        log.info("대댓글 작성: accountId={}, parentCommentId={}, replyId={}",
                accountId, parentCommentId, reply.getCommentId());

        return CommunityCommentResponseDto.of(reply, false);
    }

    @Transactional
    public CommunityCommentResponseDto updateComment(
            Long accountId,
            Long commentId,
            CommunityCommentUpdateRequestDto request
    ) {
        CommunityComment comment = getCommentOrThrow(commentId);
        validateOwner(comment, accountId);

        comment.update(request.getContent());

        boolean likedByMe = commentLikeRepository
                .existsByAccount_AccountIdAndComment_CommentId(accountId, commentId);

        return CommunityCommentResponseDto.of(comment, likedByMe);
    }

    @Transactional
    public void deleteComment(Long accountId, Long commentId) {
        CommunityComment comment = getCommentOrThrow(commentId);
        validateOwner(comment, accountId);

        CommunityPost post = comment.getPost();

        if (!comment.isReply()) {
            int deletedReplyCount = commentRepository.softDeleteRepliesByParentId(commentId);
            post.decreaseCommentCount(1 + deletedReplyCount);
        } else {
            post.decreaseCommentCount();
        }

        comment.softDelete();

        log.info("댓글/대댓글 soft-delete: accountId={}, commentId={}",
                accountId, commentId);
    }

    private Set<Long> batchFetchLikedIds(Long accountId, List<CommunityComment> comments) {
        if (accountId == null || comments.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> commentIds = comments.stream()
                .map(CommunityComment::getCommentId)
                .toList();

        return commentLikeRepository.findLikedCommentIds(accountId, commentIds);
    }

    private CommunityComment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private Account getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateOwner(CommunityComment comment, Long accountId) {
        if (!comment.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_COMMENT_ACCESS_DENIED);
        }
    }

    private void validateSameRegion(CommunityPost post, Account account) {
        if (account.getPrimaryRegionId() == null) {
            throw new NotFoundException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        }

        if (!post.getRegion().getRegionId().equals(account.getPrimaryRegionId())) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
    }
}