package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.reservation.event.ReservationCancelledEvent;
import com.eeum.eeum.domain.reservation.event.ReservationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReservationNotificationListener listener;

    @Test
    void 새_예약_신청시_상점_사장에게_NEW_RESERVATION_알림을_생성한다() {
        // given
        Long ownerAccountId = 10L;
        Long reservationId = 77L;
        ReservationCreatedEvent event = new ReservationCreatedEvent(
                ownerAccountId, "홍길동", "테스트상점",
                LocalDate.of(2026, 7, 20), LocalTime.of(18, 0), reservationId);
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onReservationCreated(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(ownerAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.NEW_RESERVATION);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.RESERVATION);
        assertThat(dto.getRefId()).isEqualTo(reservationId);
    }

    @Test
    void 사용자_예약_취소시_상점_사장에게_RESERVATION_CANCELLED_알림을_생성한다() {
        // given
        Long ownerAccountId = 10L;
        Long reservationId = 77L;
        ReservationCancelledEvent event = new ReservationCancelledEvent(
                ownerAccountId, "홍길동", "테스트상점",
                LocalDate.of(2026, 7, 20), LocalTime.of(18, 0), reservationId);
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onReservationCancelled(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(ownerAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.RESERVATION);
        assertThat(dto.getRefId()).isEqualTo(reservationId);
        assertThat(dto.getContent()).contains("취소");
    }
}
