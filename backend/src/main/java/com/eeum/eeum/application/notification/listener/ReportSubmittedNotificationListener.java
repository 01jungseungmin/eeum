package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.report.event.ReportSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSubmittedNotificationListener {

    private final NotificationService notificationService;
    private final AccountRepository accountRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportSubmitted(ReportSubmittedEvent event) {
        List<Long> adminIds = accountRepository.findAdminAccountIds();
        if (adminIds.isEmpty()) {
            log.warn("신고 접수 알림 — 관리자 계정 없음: reportId={}", event.reportId());
            return;
        }

        String content = String.format("'%s'님이 %s 신고를 접수했습니다.",
                event.reporterNickname(), event.targetType().name());

        List<NotificationCreateRequestDto> requests = adminIds.stream()
                .map(adminId -> NotificationCreateRequestDto.builder()
                        .accountId(adminId)
                        .type(NotificationType.REPORT_SUBMITTED)
                        .title("새 신고가 접수되었습니다")
                        .content(content)
                        .refType(NotificationRefType.REPORT)
                        .refId(event.reportId())
                        .linkUrl("/admin/reports/" + event.reportId())
                        .build())
                .toList();

        notificationService.createNotificationsBatch(requests);
    }
}
