package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.inquiry.event.InquiryAnsweredEvent;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryAnsweredNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInquiryAnswered(InquiryAnsweredEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.writerAccountId())
                .type(NotificationType.INQUIRY_ANSWERED)
                .title("문의 답변이 등록되었습니다")
                .content(String.format("'%s' 문의에 답변이 등록되었습니다.", event.inquiryTitle()))
                .refType(NotificationRefType.INQUIRY)
                .refId(event.inquiryId())
                .linkUrl("/inquiries/" + event.inquiryId())
                .build());
    }
}
