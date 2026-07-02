package com.eeum.eeum.application.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationTimeSlotResponseDto {

    private Long timeSlotId;

    private LocalTime time;

    private Boolean enabled;

    private Boolean closed;
}