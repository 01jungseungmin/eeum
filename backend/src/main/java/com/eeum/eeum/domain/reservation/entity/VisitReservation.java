package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
@Table(
    name = "reservation",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_reservation_table_start",
            columnNames = {"store_table_id", "reserved_start_at"}
        )
    }
)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_table_id")
    private StoreTable storeTable;

    @Column(name = "reserved_start_at")
    private LocalDateTime reservedStartAt;

    @Column(name = "reserved_end_at")
    private LocalDateTime reservedEndAt;

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
            String requestMessage,
            StoreTable storeTable,
            LocalDateTime reservedStartAt,
            LocalDateTime reservedEndAt
    ) {
        VisitReservation reservation = new VisitReservation();
        reservation.store = store;
        reservation.account = account;
        reservation.visitDate = visitDate;
        reservation.visitTime = visitTime;
        reservation.visitorCount = visitorCount != null ? visitorCount : 1;
        reservation.requestMessage = requestMessage;
        reservation.storeTable = storeTable;
        reservation.reservedStartAt = reservedStartAt;
        reservation.reservedEndAt = reservedEndAt;
        reservation.status = VisitReservationStatus.PENDING;
        return reservation;
    }

    public void approve() {
        if (this.status != VisitReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_APPROVE_NOT_ALLOWED);
        }
        this.status = VisitReservationStatus.APPROVED;
        this.rejectReason = null;
    }

    public void reject(String reason) {
        if (this.status != VisitReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_REJECT_NOT_ALLOWED);
        }
        this.status = VisitReservationStatus.REJECTED;
        this.rejectReason = reason;
        this.storeTable = null;
        this.reservedStartAt = null;
        this.reservedEndAt = null;
    }

    public void cancel() {
        if (this.status != VisitReservationStatus.PENDING && this.status != VisitReservationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
        }
        this.status = VisitReservationStatus.CANCELED;
        this.storeTable = null;
        this.reservedStartAt = null;
        this.reservedEndAt = null;
    }

    public void complete() {
        if (this.status != VisitReservationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.RESERVATION_COMPLETE_NOT_ALLOWED);
        }
        this.status = VisitReservationStatus.COMPLETED;
    }
}