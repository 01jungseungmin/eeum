package com.eeum.eeum.application.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationTimeSlotResponseDto {

    private LocalTime time;

    private Integer maxVisitorCount;

    private Integer maxTeamCount;

    private Integer reservedVisitorCount;

    private Long reservedTeamCount;

    private Boolean enabled;
}