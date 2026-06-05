package com.eeum.eeum.domain.reservation.repository;

import java.time.LocalTime;

public interface VisitReservationTimeSlotCountProjection {

    LocalTime getReservationTime();

    Long getReservedTeams();

    Long getReservedPeople();
}