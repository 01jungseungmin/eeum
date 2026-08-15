package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.store.event.StoreReviewAdminActionEvent;
import com.eeum.eeum.domain.store.event.StoreReviewCreatedEvent;
import com.eeum.eeum.domain.store.event.StoreReviewReplyCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 상점 리뷰/답글 도메인 이벤트를 알림으로 변환
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreReviewNotificationListener {

    private final NotificationService notificationService;

    // 새 리뷰 → 사장에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCreated(StoreReviewCreatedEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.ownerAccountId())
                .type(NotificationType.STORE_REVIEW)
                .title("새 리뷰가 등록되었습니다")
                .content(String.format("%s님이 %s에 리뷰를 남겼습니다.",
                        event.reviewerName(), event.storeName()))
                .refType(NotificationRefType.STORE_REVIEW)
                .refId(event.reviewId())
                .linkUrl("/owner/reviews/" + event.reviewId())
                .build());
    }

    // 사장 답글 → 리뷰 작성자에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReplyCreated(StoreReviewReplyCreatedEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.reviewerAccountId())
                .type(NotificationType.STORE_REVIEW_REPLY)
                .title("내 리뷰에 답글이 달렸습니다")
                .content(String.format("%s 사장님이 내 리뷰에 답글을 남겼습니다.", event.storeName()))
                .refType(NotificationRefType.STORE_REVIEW)
                .refId(event.reviewId())
                .linkUrl("/stores/" + event.storeId() + "/reviews/" + event.reviewId())
                .build());
    }

    // 관리자 조치 → 리뷰 작성자에게 필수 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminAction(StoreReviewAdminActionEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.targetAccountId())
                .type(NotificationType.STORE_REVIEW_ADMIN_ACTION)
                .title("관리자 조치 안내")
                .content(String.format("회원님의 리뷰에 관리자 조치가 취해졌습니다. 조치: %s, 사유: %s",
                        event.actionLabel(), event.adminNote()))
                .refType(NotificationRefType.STORE_REVIEW)
                .refId(event.reviewId())
                .linkUrl(null)
                .build());
    }
}
