package com.eeum.eeum.application.used.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.used.event.UsedProductReservationCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 판매자 탈퇴로 예약이 취소됐음을 구매자에게 알린다.
 *
 * <p>이 통보가 없으면 구매자는 약속한 거래가 사라진 것을 알 방법이 없다 —
 * 판매자가 비활성이 되는 순간 게시글이 모든 조회에서 빠지기 때문이다.
 *
 * <p>AFTER_COMMIT으로 받는다. 탈퇴가 롤백되면 예약도 그대로인데 취소 통보만 남으면 안 된다.
 *
 * <p>링크는 두지 않는다. 판매자가 비활성이라 게시글 상세는 어차피 404다.
 */
@Component
@RequiredArgsConstructor
public class UsedProductReservationCancelledListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(UsedProductReservationCancelledEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.buyerAccountId())
                .type(NotificationType.SYSTEM_NOTICE)
                .title("예약이 취소되었습니다")
                .content(String.format(
                        "판매자 탈퇴로 '%s' 예약이 취소되었습니다.", event.productTitle()))
                .refType(NotificationRefType.USED_PRODUCT)
                .refId(event.usedProductId())
                .linkUrl(null)
                .build());
    }
}
