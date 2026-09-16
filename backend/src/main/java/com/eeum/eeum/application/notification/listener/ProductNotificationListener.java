package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.product.event.ProductStockWarningEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 재고 경고 이벤트를 알림으로 변환 수신자는 가게 사장

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockWarning(ProductStockWarningEvent event) {
        String content = event.remainingStock() == 0
                ? String.format("'%s' 상품이 품절되었습니다.", event.productName())
                : String.format("'%s' 상품 재고가 %d개 남았습니다.",
                        event.productName(), event.remainingStock());
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.ownerAccountId())
                .type(NotificationType.STOCK_WARNING)
                .title("재고 부족 알림")
                .content(content)
                .refType(NotificationRefType.PRODUCT)
                .refId(event.productId())
                .linkUrl("/owner/products/" + event.productId())
                .build());
    }
}
