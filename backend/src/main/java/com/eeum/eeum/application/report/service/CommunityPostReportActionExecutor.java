package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.community.service.CommunityPostDeletionProcessor;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityPostReportActionExecutor implements ReportTargetActionExecutor {

    private final CommunityPostRepository postRepository;
    private final CommunityPostDeletionProcessor postDeletionProcessor;
    private final ReportedAccountActionService reportedAccountActionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReportTargetType targetType() {
        return ReportTargetType.COMMUNITY_POST;
    }

    @Override
    public Long execute(
            ReportAction action,
            Long postId,
            Long storedAuthorAccountId,
            String adminNote
    ) {
        Long authorAccountId = switch (action) {
            case HIDE_POST -> hidePost(postId);
            case DELETE_POST -> deletePost(postId);
            case WARN_AUTHOR, SUSPEND_AUTHOR -> applyAccountAction(
                    action,
                    resolveAuthorAccountId(postId, storedAuthorAccountId)
            );
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };

        eventPublisher.publishEvent(new CommunityAdminActionEvent(
                authorAccountId,
                NotificationRefType.COMMUNITY_POST,
                postId,
                actionLabel(action)
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
        postDeletionProcessor.deleteLockedPost(post);

        return authorAccountId;
    }

    private Long applyAccountAction(ReportAction action, Long authorAccountId) {
        return reportedAccountActionService.apply(action, authorAccountId);
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
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
    }
}
