package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.account.event.OwnerApplicationSubmittedEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// 사장 심사 요청 접수 이벤트를 알림으로 변환 수신자는 관리자(ROLE_ADMIN) 전체
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerApplicationNotificationListener {

    private final NotificationService notificationService;
    private final AccountRepository accountRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOwnerApplicationSubmitted(OwnerApplicationSubmittedEvent event) {
        List<Long> adminIds = accountRepository.findAdminAccountIds();
        if (adminIds.isEmpty()) {
            log.warn("사장 심사 요청 알림 — 관리자 계정 없음: applicantAccountId={}", event.applicantAccountId());
            return;
        }

        List<NotificationCreateRequestDto> requests = adminIds.stream()
                .map(adminId -> NotificationCreateRequestDto.builder()
                        .accountId(adminId)
                        .type(NotificationType.OWNER_APPLICATION_SUBMITTED)
                        .title("새 사장 입점 심사 요청")
                        .content(String.format("%s님(%s)이 입점 심사를 요청했습니다.",
                                event.applicantName(), event.storeName()))
                        .refType(NotificationRefType.OWNER_APPLICATION)
                        .refId(event.applicantAccountId())
                        .linkUrl("/admin/owner-applications/" + event.applicantAccountId())
                        .build())
                .toList();

        notificationService.createNotificationsBatch(requests);
    }
}
