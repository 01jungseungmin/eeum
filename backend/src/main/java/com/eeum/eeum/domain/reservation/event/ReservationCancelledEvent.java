package com.eeum.eeum.domain.reservation.event;

import java.time.LocalDate;
import java.time.LocalTime;

// 사용자가 방문 예약을 취소했을 때 발행 수신자는 가게 사장
// NotificationType.RESERVATION_CANCELLED 알림으로 변환

public record ReservationCancelledEvent(
        Long ownerAccountId,
        String customerName,
        LocalDate visitDate,
        LocalTime visitTime,
        Long reservationId
) {
}
