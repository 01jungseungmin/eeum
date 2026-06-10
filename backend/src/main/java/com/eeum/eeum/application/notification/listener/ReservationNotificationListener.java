package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.reservation.event.ReservationApprovedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationCreatedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

// 예약 도메인 이벤트를 알림으로 변환 예약 트랜잭션이 커밋된 뒤(AFTER_COMMIT) 비동기로 실행(알림 생성 실패가 예약 처리에 영향 X)
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationNotificationListener {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M월 d일");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;

    // 새 예약 신청 → 사장에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        String when = format(event.visitDate(), event.visitTime());
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.ownerAccountId())
                .type(NotificationType.NEW_RESERVATION)
                .title("새 방문 예약 신청")
                .content(String.format("%s님이 %s 방문 예약을 신청했습니다.", event.customerName(), when))
                .refType(NotificationRefType.RESERVATION)
                .refId(event.reservationId())
                .linkUrl("/owner/reservations/" + event.reservationId())
                .build());
    }

    // 예약 승인 → 사용자에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationApproved(ReservationApprovedEvent event) {
        String when = format(event.visitDate(), event.visitTime());
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.customerAccountId())
                .type(NotificationType.RESERVATION_CONFIRMED)
                .title("예약이 확정되었습니다")
                .content(String.format("%s %s 방문 예약이 확정되었습니다.", event.storeName(), when))
                .refType(NotificationRefType.RESERVATION)
                .refId(event.reservationId())
                .linkUrl("/reservations/" + event.reservationId())
                .build());
    }

    // 예약 거절 → 사용자에게 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationRejected(ReservationRejectedEvent event) {
        String when = format(event.visitDate(), event.visitTime());
        String content = String.format("%s %s 방문 예약이 거절되었습니다.", event.storeName(), when);
        if (event.rejectReason() != null && !event.rejectReason().isBlank()) {
            content += " 사유: " + event.rejectReason();
        }
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.customerAccountId())
                .type(NotificationType.RESERVATION_CANCELLED)
                .title("예약이 취소되었습니다")
                .content(content)
                .refType(NotificationRefType.RESERVATION)
                .refId(event.reservationId())
                .linkUrl("/reservations/" + event.reservationId())
                .build());
    }

    private String format(java.time.LocalDate date, java.time.LocalTime time) {
        return date.format(DATE_FMT) + " " + time.format(TIME_FMT);
    }
}
