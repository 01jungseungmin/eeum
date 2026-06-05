package com.eeum.eeum.application.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationTimeSlotResponseDto {

    private Long timeSlotId;

    private LocalTime time;

    private Integer maxVisitorCount;

    private Integer maxTeamCount;

    private Integer reservedVisitorCount;

    private Long reservedTeamCount;

    private Boolean enabled;

    /** 팀 정원 or 인원 정원 초과, 혹은 disabled 슬롯이면 true */
    private Boolean closed;
}