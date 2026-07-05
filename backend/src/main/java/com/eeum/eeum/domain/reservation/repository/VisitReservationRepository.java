package com.eeum.eeum.domain.reservation.repository;

import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VisitReservationRepository extends JpaRepository<VisitReservation, Long> {

    @EntityGraph(attributePaths = {"store", "account", "storeTable"})
    Optional<VisitReservation> findByVisitReservationId(Long visitReservationId);

    @EntityGraph(attributePaths = {"store", "account", "storeTable"})
    Page<VisitReservation> findByAccount_AccountIdOrderByVisitDateDescVisitTimeDesc(
            Long accountId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"store", "account", "storeTable"})
    Page<VisitReservation> findByStore_StoreIdOrderByVisitDateDescVisitTimeDesc(
            Long storeId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"store", "account", "storeTable"})
    Page<VisitReservation> findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(
            Long storeId,
            VisitReservationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"store", "account", "storeTable"})
    Optional<VisitReservation> findByVisitReservationIdAndAccount_AccountId(
            Long visitReservationId,
            Long accountId
    );

    boolean existsByAccount_AccountIdAndStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
            Long accountId,
            Long storeId,
            LocalDate visitDate,
            LocalTime visitTime,
            Collection<VisitReservationStatus> statuses
    );

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM VisitReservation r
        WHERE r.store.storeId = :storeId
          AND r.status IN :statuses
          AND (r.reservedEndAt > :now
               OR (r.reservedEndAt IS NULL AND r.visitDate >= :today))
    """)
    boolean existsActiveFutureReservations(
            @Param("storeId") Long storeId,
            @Param("statuses") Collection<VisitReservationStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("today") LocalDate today
    );

    long countByStore_StoreIdAndStatus(
            Long storeId,
            VisitReservationStatus status
    );

    @Query("""
        SELECT DISTINCT r.storeTable.storeTableId
        FROM VisitReservation r
        WHERE r.store.storeId = :storeId
          AND r.storeTable IS NOT NULL
          AND r.status IN :statuses
          AND r.reservedStartAt < :endAt
          AND r.reservedEndAt > :startAt
    """)
    List<Long> findOccupiedTableIds(
            @Param("storeId") Long storeId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("statuses") List<VisitReservationStatus> statuses
    );

    @Query("""
        SELECT r.storeTable.storeTableId AS tableId,
               r.reservedStartAt AS startAt,
               r.reservedEndAt AS endAt
        FROM VisitReservation r
        WHERE r.store.storeId = :storeId
          AND r.storeTable IS NOT NULL
          AND r.status IN :statuses
          AND r.reservedStartAt < :dayEnd
          AND r.reservedEndAt > :dayStart
    """)
    List<ReservationOccupancyProjection> findDayOccupancies(
            @Param("storeId") Long storeId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("statuses") List<VisitReservationStatus> statuses
    );
}
