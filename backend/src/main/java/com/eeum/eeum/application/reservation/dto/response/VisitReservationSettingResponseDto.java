package com.eeum.eeum.application.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationSettingResponseDto {

    private Boolean enabled;

    private Integer slotIntervalMinutes;

    private Boolean sameDayReservationAllowed;

    private Integer cancelDeadlineMinutes;

    private LocalTime startTime;

    private LocalTime endTime;
}