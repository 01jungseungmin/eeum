package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.event.InquirySubmittedEvent;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
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
public class InquirySubmittedNotificationListener {

    private final NotificationService notificationService;
    private final AccountRepository accountRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInquirySubmitted(InquirySubmittedEvent event) {
        String content = String.format("'%s'님이 새 문의를 등록했습니다: %s",
                event.writerNickname(), event.inquiryTitle());

        if (event.targetType() == InquiryTargetType.STORE) {
            notificationService.createNotification(NotificationCreateRequestDto.builder()
                    .accountId(event.storeOwnerAccountId())
                    .type(NotificationType.INQUIRY_SUBMITTED)
                    .title("새 문의가 접수되었습니다")
                    .content(content)
                    .refType(NotificationRefType.INQUIRY)
                    .refId(event.inquiryId())
                    .linkUrl("/owner/stores/me/inquiries/" + event.inquiryId())
                    .build());
            return;
        }

        // ADMIN 문의 — 관리자 전체에게 발송
        List<Long> adminIds = accountRepository.findAdminAccountIds();
        if (adminIds.isEmpty()) {
            log.warn("문의 접수 알림 — 관리자 계정 없음: inquiryId={}", event.inquiryId());
            return;
        }

        List<NotificationCreateRequestDto> requests = adminIds.stream()
                .map(adminId -> NotificationCreateRequestDto.builder()
                        .accountId(adminId)
                        .type(NotificationType.INQUIRY_SUBMITTED)
                        .title("새 문의가 접수되었습니다")
                        .content(content)
                        .refType(NotificationRefType.INQUIRY)
                        .refId(event.inquiryId())
                        .linkUrl("/admin/inquiries/" + event.inquiryId())
                        .build())
                .toList();

        notificationService.createNotificationsBatch(requests);
    }
}
