package com.eeum.eeum.domain.reservation.event;

import java.time.LocalDate;
import java.time.LocalTime;

// 사장이 방문 예약을 거절했을 때 발행
// 수신자는 예약한 사용자 NotificationType.RESERVATION_CANCELLED 알림으로 변환

public record ReservationRejectedEvent(
        Long customerAccountId,
        String storeName,
        LocalDate visitDate,
        LocalTime visitTime,
        String rejectReason,
        Long reservationId
) {
}
