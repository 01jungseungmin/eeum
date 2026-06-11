package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Entity
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_reservation_id")
    private Long visitReservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "visit_time", nullable = false)
    private LocalTime visitTime;

    @Column(name = "visitor_count", nullable = false)
    private Integer visitorCount = 1;

    @Column(name = "request_message", length = 500)
    private String requestMessage;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VisitReservationStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static VisitReservation create(
            Store store,
            Account account,
            LocalDate visitDate,
            LocalTime visitTime,
            Integer visitorCount,
            String requestMessage
    ) {
        VisitReservation reservation = new VisitReservation();
        reservation.store = store;
        reservation.account = account;
        reservation.visitDate = visitDate;
        reservation.visitTime = visitTime;
        reservation.visitorCount = visitorCount != null ? visitorCount : 1;
        reservation.requestMessage = requestMessage;
        reservation.status = VisitReservationStatus.PENDING;
        return reservation;
    }

    public void approve() {
        if (this.status != VisitReservationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 예약만 승인할 수 있습니다.");
        }

        this.status = VisitReservationStatus.APPROVED;
        this.rejectReason = null;
    }

    public void reject(String reason) {
        if (this.status != VisitReservationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 예약만 거절할 수 있습니다.");
        }

        this.status = VisitReservationStatus.REJECTED;
        this.rejectReason = reason;
    }

    public void cancel() {
        if (this.status == VisitReservationStatus.COMPLETED) {
            throw new IllegalStateException("완료된 예약은 취소할 수 없습니다.");
        }

        this.status = VisitReservationStatus.CANCELED;
    }

    public void complete() {
        if (this.status != VisitReservationStatus.APPROVED) {
            throw new IllegalStateException("승인된 예약만 완료 처리할 수 있습니다.");
        }

        this.status = VisitReservationStatus.COMPLETED;
    }
}