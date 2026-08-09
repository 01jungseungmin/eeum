package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityPostReportActionExecutor {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityImageRepository imageRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Long execute(
            ReportAction action,
            Long postId,
            Long storedAuthorAccountId,
            String adminNote
    ) {
        Long authorAccountId = switch (action) {
            case HIDE_POST -> hidePost(postId);
            case DELETE_POST -> deletePost(postId);
            case WARN_AUTHOR -> resolveAuthorAccountId(postId, storedAuthorAccountId);
            case SUSPEND_AUTHOR -> suspendAuthor(postId, storedAuthorAccountId);
            case DISMISS -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };

        eventPublisher.publishEvent(new CommunityAdminActionEvent(
                authorAccountId,
                NotificationRefType.COMMUNITY_POST,
                postId,
                actionLabel(action) + ": " + adminNote
        ));
        return authorAccountId;
    }

    private Long hidePost(Long postId) {
        CommunityPost post = getPostForUpdate(postId);
        post.hide();
        return post.getAccount().getAccountId();
    }

    private Long deletePost(Long postId) {
        CommunityPost post = getPostForUpdate(postId);
        Long authorAccountId = post.getAccount().getAccountId();

        commentLikeRepository.deleteByComment_Post_PostId(postId);
        commentRepository.deleteRepliesByPost_PostId(postId);
        commentRepository.deleteTopLevelCommentsByPost_PostId(postId);
        postLikeRepository.deleteByPost_PostId(postId);
        imageRepository.deleteByPost_PostId(postId);
        postRepository.delete(post);

        return authorAccountId;
    }

    private Long suspendAuthor(Long postId, Long storedAuthorAccountId) {
        Long authorAccountId = resolveAuthorAccountId(postId, storedAuthorAccountId);
        Account author = accountRepository.findByIdWithLock(authorAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));

        if (author.isAdmin()) {
            throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        }
        if (author.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        author.suspend();
        eventPublisher.publishEvent(AccountTokenCleanupEvent.refreshOnly(authorAccountId));
        return authorAccountId;
    }

    private Long resolveAuthorAccountId(Long postId, Long storedAuthorAccountId) {
        if (storedAuthorAccountId != null) {
            return storedAuthorAccountId;
        }
        return getPostForUpdate(postId).getAccount().getAccountId();
    }

    private CommunityPost getPostForUpdate(Long postId) {
        return postRepository.findWithAccountByPostIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
    }

    private String actionLabel(ReportAction action) {
        return switch (action) {
            case HIDE_POST -> "게시글 숨김";
            case DELETE_POST -> "게시글 삭제";
            case WARN_AUTHOR -> "작성자 경고";
            case SUSPEND_AUTHOR -> "작성자 정지";
            case DISMISS -> "신고 기각";
        };
    }
}
