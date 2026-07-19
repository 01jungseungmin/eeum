package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.event.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderNotificationListener listener;

    @Test
    void 새_주문_발생시_상점_사장에게_NEW_ORDER_알림을_생성한다() {
        // given
        Long ownerAccountId = 10L;
        Long orderId = 55L;
        OrderPlacedEvent event = new OrderPlacedEvent(
                ownerAccountId, "홍길동", "테스트상점", "ORD-20260718-0001", orderId);
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onOrderPlaced(event);

        // then: 수신자는 주문자가 아니라 상점 사장, 참조는 ORDER/orderId
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(ownerAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.NEW_ORDER);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.ORDER);
        assertThat(dto.getRefId()).isEqualTo(orderId);
        assertThat(dto.getContent()).contains("홍길동");
    }

    @Test
    void 주문_상태_변경시_주문자에게_ORDER_STATUS_CHANGED_알림을_생성한다() {
        // given
        Long customerAccountId = 20L;
        Long orderId = 55L;
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                customerAccountId, "테스트상점", "ORD-20260718-0001", "주문 확인", orderId);
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onOrderStatusChanged(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(customerAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.ORDER_STATUS_CHANGED);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.ORDER);
        assertThat(dto.getRefId()).isEqualTo(orderId);
    }
}
