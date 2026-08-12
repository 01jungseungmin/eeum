package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
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
public class CommunityCommentReportActionExecutor implements ReportTargetActionExecutor {

    private final CommunityCommentRepository commentRepository;
    private final CommunityPostRepository postRepository;
    private final ReportedAccountActionService reportedAccountActionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReportTargetType targetType() {
        return ReportTargetType.COMMUNITY_COMMENT;
    }

    @Override
    public Long execute(
            ReportAction action,
            Long commentId,
            Long storedAuthorAccountId,
            String adminNote
    ) {
        Long authorAccountId;
        if (action == ReportAction.DELETE_COMMENT) {
            authorAccountId = deleteComment(commentId);
        } else if (action == ReportAction.WARN_AUTHOR || action == ReportAction.SUSPEND_AUTHOR) {
            authorAccountId = reportedAccountActionService.apply(
                    action,
                    resolveAuthorAccountId(commentId, storedAuthorAccountId)
            );
        } else {
            throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        }

        eventPublisher.publishEvent(new CommunityAdminActionEvent(
                authorAccountId,
                NotificationRefType.COMMUNITY_COMMENT,
                commentId,
                actionLabel(action)
        ));
        return authorAccountId;
    }

    private Long deleteComment(Long commentId) {
        Long postId = commentRepository.findPostIdByCommentId(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        postRepository.findWithAccountByPostIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        CommunityComment comment = getCommentForUpdate(commentId);
        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
        }

        Long authorAccountId = comment.getAccount().getAccountId();
        comment.softDelete();
        postRepository.decreaseCommentCount(postId);
        return authorAccountId;
    }

    private Long resolveAuthorAccountId(Long commentId, Long storedAuthorAccountId) {
        if (storedAuthorAccountId != null) {
            return storedAuthorAccountId;
        }
        return getCommentForUpdate(commentId).getAccount().getAccountId();
    }

    private CommunityComment getCommentForUpdate(Long commentId) {
        return commentRepository.findWithAccountByCommentIdForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
    }

    private String actionLabel(ReportAction action) {
        if (action == ReportAction.DELETE_COMMENT) {
            return "댓글 삭제";
        }
        if (action == ReportAction.WARN_AUTHOR) {
            return "작성자 경고";
        }
        if (action == ReportAction.SUSPEND_AUTHOR) {
            return "작성자 정지";
        }
        throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
    }
}
