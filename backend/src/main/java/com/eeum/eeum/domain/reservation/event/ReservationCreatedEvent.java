package com.eeum.eeum.domain.reservation.event;

import java.time.LocalDate;
import java.time.LocalTime;

// 방문 예약 신청 완료 시 발행 수신자는 가게 사장
// NotificationType.NEW_RESERVATION 알림으로 변환

public record ReservationCreatedEvent(
        Long ownerAccountId,
        String customerName,
        String storeName,
        LocalDate visitDate,
        LocalTime visitTime,
        Long reservationId
) {
}
