package com.eeum.eeum.application.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VisitReservationSettingResponseDto {

    private Boolean enabled;

    private Integer defaultMaxVisitorCount;

    private Integer defaultMaxTeamCount;

    private Integer slotIntervalMinutes;

    private Boolean sameDayReservationAllowed;

    private Integer cancelDeadlineMinutes;
}