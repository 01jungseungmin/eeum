package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountReportActionExecutor implements ReportTargetActionExecutor {

    private final ReportedAccountActionService reportedAccountActionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReportTargetType targetType() {
        return ReportTargetType.ACCOUNT;
    }

    @Override
    public Long execute(
            ReportAction action,
            Long accountId,
            Long storedOwnerAccountId,
            String adminNote
    ) {
        if (action != ReportAction.WARN_AUTHOR && action != ReportAction.SUSPEND_AUTHOR) {
            throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        }
        Long actionTargetAccountId = reportedAccountActionService.apply(action, accountId);
        eventPublisher.publishEvent(new ReportActionNotificationEvent(
                actionTargetAccountId,
                NotificationRefType.SYSTEM,
                null,
                actionLabel(action)
        ));
        return actionTargetAccountId;
    }

    private String actionLabel(ReportAction action) {
        return switch (action) {
            case WARN_AUTHOR -> "회원 경고";
            case SUSPEND_AUTHOR -> "회원 정지";
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
    }
}
