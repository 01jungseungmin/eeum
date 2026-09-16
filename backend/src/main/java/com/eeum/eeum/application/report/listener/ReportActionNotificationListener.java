package com.eeum.eeum.application.report.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportActionNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportAction(ReportActionNotificationEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.targetAccountId())
                .type(NotificationType.SYSTEM_NOTICE)
                .title("관리자 조치 안내")
                .content(String.format(
                        "신고 검토 결과 관리자 조치가 적용되었습니다. 조치: %s, 사유: %s",
                        event.actionLabel(), event.adminNote()))
                .refType(event.refType())
                .refId(event.refId())
                .linkUrl(null)
                .build());
    }
}
