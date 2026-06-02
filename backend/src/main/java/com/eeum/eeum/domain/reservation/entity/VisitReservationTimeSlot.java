package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "visit_reservation_time_slot")
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

    @Column(name = "max_visitor_count", nullable = false)
    private Integer maxVisitorCount;

    @Column(name = "max_team_count", nullable = false)
    private Integer maxTeamCount;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public static VisitReservationTimeSlot create(
            Store store,
            LocalDate slotDate,
            LocalTime slotTime,
            Integer maxVisitorCount,
            Integer maxTeamCount
    ) {
        VisitReservationTimeSlot slot = new VisitReservationTimeSlot();
        slot.store = store;
        slot.slotDate = slotDate;
        slot.slotTime = slotTime;
        slot.maxVisitorCount = maxVisitorCount;
        slot.maxTeamCount = maxTeamCount;
        return slot;
    }

    public void update(
            Integer maxVisitorCount,
            Integer maxTeamCount,
            boolean enabled
    ) {
        this.maxVisitorCount = maxVisitorCount;
        this.maxTeamCount = maxTeamCount;
        this.enabled = enabled;
    }
}