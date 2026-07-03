package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "visit_reservation_time_slot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_visit_reservation_time_slot_store_date_time",
                        columnNames = {"store_id", "slot_date", "slot_time"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_visit_reservation_time_slot_store_date",
                        columnList = "store_id, slot_date"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitReservationTimeSlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_slot_id")
    private Long timeSlotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public static VisitReservationTimeSlot create(
            Store store,
            LocalDate slotDate,
            LocalTime slotTime,
            Boolean enabled
    ) {
        VisitReservationTimeSlot slot = new VisitReservationTimeSlot();
        slot.store = store;
        slot.slotDate = slotDate;
        slot.slotTime = slotTime;
        slot.enabled = enabled != null ? enabled : true;
        return slot;
    }

    public void update(
            boolean enabled
    ) {
        this.enabled = enabled;
    }
}