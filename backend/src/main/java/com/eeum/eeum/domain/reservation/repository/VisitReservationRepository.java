package com.eeum.eeum.domain.reservation.repository;

import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

public interface VisitReservationRepository extends JpaRepository<VisitReservation, Long> {

    Page<VisitReservation> findByAccount_AccountIdOrderByVisitDateDescVisitTimeDesc(
            Long accountId,
            Pageable pageable
    );

    Page<VisitReservation> findByStore_StoreIdOrderByVisitDateDescVisitTimeDesc(
            Long storeId,
            Pageable pageable
    );

    Page<VisitReservation> findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(
            Long storeId,
            VisitReservationStatus status,
            Pageable pageable
    );

    Page<VisitReservation> findByStore_StoreIdAndVisitDateOrderByVisitTimeAsc(
            Long storeId,
            LocalDate visitDate,
            Pageable pageable
    );

    Page<VisitReservation> findByStore_StoreIdAndVisitDateAndStatusOrderByVisitTimeAsc(
            Long storeId,
            LocalDate visitDate,
            VisitReservationStatus status,
            Pageable pageable
    );

    @Query("""
        select coalesce(sum(r.visitorCount), 0)
        from VisitReservation r
        where r.store.storeId = :storeId
          and r.visitDate = :visitDate
          and r.visitTime = :visitTime
          and r.status in :statuses
    """)
    Integer sumVisitorCountByTimeSlot(
            Long storeId,
            LocalDate visitDate,
            LocalTime visitTime,
            Collection<VisitReservationStatus> statuses
    );

    long countByStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
            Long storeId,
            LocalDate visitDate,
            LocalTime visitTime,
            Collection<VisitReservationStatus> statuses
    );

    boolean existsByAccount_AccountIdAndStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
            Long accountId,
            Long storeId,
            LocalDate visitDate,
            LocalTime visitTime,
            Collection<VisitReservationStatus> statuses
    );

    long countByStore_StoreIdAndStatus(
            Long storeId,
            VisitReservationStatus status
    );

    @Query("""
    SELECT r.visitTime          AS reservationTime,
           SUM(r.visitorCount)  AS reservedPeople,
           COUNT(r)             AS reservedTeams
    FROM VisitReservation r
        where r.store.storeId = :storeId
          and r.visitDate = :date
          and r.status in :statuses
        group by r.visitTime
    """)
    List<VisitReservationTimeSlotCountProjection> countTimeSlotsByStoreAndDate(
            @Param("storeId") Long storeId,
            @Param("date") LocalDate date,
            @Param("statuses") List<VisitReservationStatus> statuses
    );

    List<VisitReservation> findByStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(Long storeId, LocalDate date, LocalTime slotTime, List<VisitReservationStatus> pending);
}