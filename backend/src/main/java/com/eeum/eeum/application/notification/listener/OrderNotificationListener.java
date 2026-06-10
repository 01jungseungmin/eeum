package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//주문/결제 도메인 이벤트를 알림으로 변환 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final NotificationService notificationService;

    // 새 주문 → 사장에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.ownerAccountId())
                .type(NotificationType.NEW_ORDER)
                .title("새 주문이 접수되었습니다")
                .content(String.format("%s님의 새 주문(%s)이 접수되었습니다.",
                        event.customerName(), event.orderNumber()))
                .refType(NotificationRefType.ORDER)
                .refId(event.orderId())
                .linkUrl("/owner/orders/" + event.orderId())
                .build());
    }

    // 결제 완료 → 사용자에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.customerAccountId())
                .type(NotificationType.PAYMENT_COMPLETED)
                .title("결제가 완료되었습니다")
                .content(String.format("%s 주문(%s) 결제가 완료되었습니다.",
                        event.storeName(), event.orderNumber()))
                .refType(NotificationRefType.PAYMENT)
                .refId(event.orderId())
                .linkUrl("/orders/" + event.orderId())
                .build());
    }

    // 주문 상태 변경 → 사용자에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.customerAccountId())
                .type(NotificationType.ORDER_STATUS_CHANGED)
                .title("주문 상태가 변경되었습니다")
                .content(String.format("%s 주문(%s) 상태가 '%s'(으)로 변경되었습니다.",
                        event.storeName(), event.orderNumber(), event.statusLabel()))
                .refType(NotificationRefType.ORDER)
                .refId(event.orderId())
                .linkUrl("/orders/" + event.orderId())
                .build());
    }
}
