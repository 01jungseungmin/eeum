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
        postRepository.increaseCommentCount(postId);

        log.info("댓글 작성: accountId={}, postId={}, commentId={}",
                accountId, postId, comment.getCommentId());

        if (!accountId.equals(post.getAccount().getAccountId())) {
            eventPublisher.publishEvent(new CommunityCommentCreatedEvent(
                    post.getAccount().getAccountId(),
                    accountId,
                    postId,
                    comment.getCommentId(),
                    account.getNickname(),
                    post.getTitle()
            ));
        }

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
        postRepository.increaseCommentCount(parent.getPost().getPostId());

        log.info("대댓글 작성: accountId={}, parentCommentId={}, replyId={}",
                accountId, parentCommentId, reply.getCommentId());

        if (!accountId.equals(parent.getAccount().getAccountId())) {
            eventPublisher.publishEvent(new CommunityReplyCreatedEvent(
                    parent.getAccount().getAccountId(),
                    accountId,
                    parent.getPost().getPostId(),
                    parentCommentId,
                    reply.getCommentId(),
                    account.getNickname()
            ));
        }

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
        validateNotDeleted(comment);

        comment.update(request.getContent());

        boolean likedByMe = commentLikeRepository
                .existsByAccount_AccountIdAndComment_CommentId(accountId, commentId);

        return CommunityCommentResponseDto.of(comment, likedByMe);
    }

    @Transactional
    public void deleteComment(Long accountId, Long commentId) {
        CommunityComment snapshot = getCommentOrThrow(commentId);
        postRepository.findWithAccountByPostIdForUpdate(snapshot.getPost().getPostId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        CommunityComment comment = commentRepository.findWithAccountByCommentIdForUpdate(commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        validateOwner(comment, accountId);
        // 이미 삭제된 댓글 재삭제 차단 — 검증 없이 재실행하면 decreaseCommentCount가 중복 호출되어
        // 게시글 commentCount가 실제 댓글 수보다 작아진다(중복 클릭/다기기 동시 삭제).
        validateNotDeleted(comment);

        comment.softDelete();
        postRepository.decreaseCommentCount(comment.getPost().getPostId());

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

    private void validateNotDeleted(CommunityComment comment) {
        if (comment.isDeleted()) {
            throw new NotFoundException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
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
