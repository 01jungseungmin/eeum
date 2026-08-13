package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.request.CommunityCommentCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityCommentUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityCommentResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityCommentCreatedEvent;
import com.eeum.eeum.domain.community.event.CommunityReplyCreatedEvent;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private final AccountRegionRepository accountRegionRepository;
    private final ApplicationEventPublisher eventPublisher;

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
    public Page<CommunityCommentResponseDto> getMyComments(Long accountId, Pageable pageable) {
        Page<CommunityComment> comments = commentRepository
                .findByAccount_AccountIdAndDeletedFalseOrderByCreatedAtDesc(accountId, pageable);

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

        CommunityPost visiblePost = getPostOrThrow(parent.getPost().getPostId());
        validateSameRegion(visiblePost, account);

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommunityCommentResponseDto createComment(
            Long accountId,
            Long postId,
            CommunityCommentCreateRequestDto request
    ) {
        Account account = getAccountOrThrow(accountId);
        CommunityPost post = getVisiblePostForUpdateOrThrow(
                postId,
                ErrorCode.COMMUNITY_POST_NOT_FOUND
        );

        validateSameRegion(post, account);

        CommunityComment comment = CommunityComment.createComment(
                post,
                account,
                request.getContent()
        );

        commentRepository.save(comment);
        CommunityCommentResponseDto response = CommunityCommentResponseDto.of(comment, false);
        Long postAuthorAccountId = post.getAccount().getAccountId();
        String postTitle = post.getTitle();
        postRepository.increaseCommentCount(postId);

        log.info("댓글 작성: accountId={}, postId={}, commentId={}",
                accountId, postId, comment.getCommentId());

        if (!accountId.equals(postAuthorAccountId)) {
            eventPublisher.publishEvent(new CommunityCommentCreatedEvent(
                    postAuthorAccountId,
                    accountId,
                    postId,
                    comment.getCommentId(),
                    account.getNickname(),
                    postTitle
            ));
        }

        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommunityCommentResponseDto createReply(
            Long accountId,
            Long parentCommentId,
            CommunityCommentCreateRequestDto request
    ) {
        Account account = getAccountOrThrow(accountId);
        Long postId = getPostIdByCommentOrThrow(parentCommentId);
        CommunityPost post = getVisiblePostForUpdateOrThrow(
                postId,
                ErrorCode.COMMUNITY_COMMENT_NOT_FOUND
        );
        CommunityComment parent = getCommentForUpdateOrThrow(parentCommentId);

        validateNotDeleted(parent);
        if (parent.isReply()) {
            throw new BadRequestException(ErrorCode.COMMUNITY_REPLY_DEPTH_EXCEEDED);
        }

        validateSameRegion(post, account);

        CommunityComment reply = CommunityComment.createReply(
                post,
                account,
                parent,
                request.getContent()
        );

        commentRepository.save(reply);
        CommunityCommentResponseDto response = CommunityCommentResponseDto.of(reply, false);
        Long parentAuthorAccountId = parent.getAccount().getAccountId();
        postRepository.increaseCommentCount(postId);

        log.info("대댓글 작성: accountId={}, parentCommentId={}, replyId={}",
                accountId, parentCommentId, reply.getCommentId());

        if (!accountId.equals(parentAuthorAccountId)) {
            eventPublisher.publishEvent(new CommunityReplyCreatedEvent(
                    parentAuthorAccountId,
                    accountId,
                    postId,
                    parentCommentId,
                    reply.getCommentId(),
                    account.getNickname()
            ));
        }

        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommunityCommentResponseDto updateComment(
            Long accountId,
            Long commentId,
            CommunityCommentUpdateRequestDto request
    ) {
        Long postId = getPostIdByCommentOrThrow(commentId);
        getVisiblePostForUpdateOrThrow(postId, ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        CommunityComment comment = getCommentForUpdateOrThrow(commentId);
        validateOwner(comment, accountId);
        validateNotDeleted(comment);

        comment.update(request.getContent());

        boolean likedByMe = commentLikeRepository
                .existsByAccount_AccountIdAndComment_CommentId(accountId, commentId);

        return CommunityCommentResponseDto.of(comment, likedByMe);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteComment(Long accountId, Long commentId) {
        Long postId = getPostIdByCommentOrThrow(commentId);
        getPostForUpdateOrThrow(postId, ErrorCode.COMMUNITY_POST_NOT_FOUND);
        CommunityComment comment = getCommentForUpdateOrThrow(commentId);
        validateOwner(comment, accountId);
        // 이미 삭제된 댓글 재삭제 차단 — 검증 없이 재실행하면 decreaseCommentCount가 중복 호출되어
        // 게시글 commentCount가 실제 댓글 수보다 작아진다(중복 클릭/다기기 동시 삭제).
        validateNotDeleted(comment);

        comment.softDelete();
        postRepository.decreaseCommentCount(postId);

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

    private Long getPostIdByCommentOrThrow(Long commentId) {
        return commentRepository.findPostIdByCommentId(commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
    }

    private CommunityComment getCommentForUpdateOrThrow(Long commentId) {
        return commentRepository.findWithAccountByCommentIdForUpdate(commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
    }

    private void validateNotDeleted(CommunityComment comment) {
        if (comment.isDeleted()) {
            throw new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private CommunityPost getPostForUpdateOrThrow(Long postId, ErrorCode errorCode) {
        return postRepository.findWithAccountByPostIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(errorCode));
    }

    private CommunityPost getVisiblePostForUpdateOrThrow(Long postId, ErrorCode errorCode) {
        return postRepository.findVisibleByPostIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(errorCode));
    }

    private Account getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Region getPrimaryRegion(Account account) {
        Long primaryAccountRegionId = account.getPrimaryRegionId();

        if (primaryAccountRegionId == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        }

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

    private void validateOwner(CommunityComment comment, Long accountId) {
        if (!comment.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_COMMENT_ACCESS_DENIED);
        }
    }

    private void validateSameRegion(CommunityPost post, Account account) {
        Region myRegion = getPrimaryRegion(account);

        if (!post.getRegion().getRegionId().equals(myRegion.getRegionId())) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
    }
}
