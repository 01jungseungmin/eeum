package com.eeum.eeum.domain.reservation.repository;

import com.eeum.eeum.domain.reservation.entity.VisitReservationTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface VisitReservationTimeSlotRepository
        extends JpaRepository<VisitReservationTimeSlot, Long> {

    Optional<VisitReservationTimeSlot> findByStore_StoreIdAndSlotDateAndSlotTime(
            Long storeId,
            LocalDate slotDate,
            LocalTime slotTime
    );

    List<VisitReservationTimeSlot> findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(
            Long storeId,
            LocalDate slotDate
    );
}