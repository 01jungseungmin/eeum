package com.eeum.eeum.domain.reservation.repository;

import java.time.LocalDateTime;

public interface ReservationOccupancyProjection {
    Long getTableId();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
}
