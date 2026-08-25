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

/**
 * 거래 완료 후 구매자에게 후기 작성을 요청한다.
 *
 * <p>{@code NotificationType.USED_REVIEW}는 "리뷰 요청"이다 — 후기가 등록됐다는 알림이 아니다.
 * 그래서 수신자는 후기를 쓸 사람(구매자)이고, 발송 시점은 판매완료다.
 *
 * <p>AFTER_COMMIT으로 받는다. 판매완료가 롤백되면 후기를 쓸 수 없는 거래인데
 * 알림만 남는 상황을 막는다.
 *
 * <p>참조는 후기(USED_REVIEW)가 아니라 게시글이다 — 이 시점에는 후기가 아직 없어서
 * 가리킬 대상이 없고, 사용자가 이동해야 할 곳도 후기를 쓸 게시글이다.
 */
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
