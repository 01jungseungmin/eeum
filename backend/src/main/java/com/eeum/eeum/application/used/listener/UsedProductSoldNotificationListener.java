package com.eeum.eeum.application.used.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.used.event.UsedProductSoldEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 거래 완료 후 구매자에게 후기 작성을 요청한다. */
@Component
@RequiredArgsConstructor
public class UsedProductSoldNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSold(UsedProductSoldEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.buyerAccountId())
                .type(NotificationType.USED_REVIEW)
                .title("거래는 어떠셨나요?")
                .content(String.format("'%s' 거래 후기를 남겨주세요.", event.productTitle()))
                .refType(NotificationRefType.USED_PRODUCT)
                .refId(event.usedProductId())
                // 게시글 상세가 아니라 후기 작성 화면으로 보낸다. 후기 자격은 거래 사실 기준이라
                // 판매자가 글을 지운 뒤에도 후기를 쓸 수 있는데, 상세는 삭제 글에 404를 준다.
                // 이 알림은 게시글이 사라져도 유효하므로 삭제 경로에서 함께 지우지 않는다.
                .linkUrl("/used/" + event.usedProductId() + "/reviews")
                .build());
    }
}
