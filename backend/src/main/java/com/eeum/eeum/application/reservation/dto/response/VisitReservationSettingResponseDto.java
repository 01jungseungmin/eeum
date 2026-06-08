package com.eeum.eeum.application.reservation.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationSettingResponseDto {

    private Boolean enabled;

    private Integer defaultMaxVisitorCount;

    private Integer defaultMaxTeamCount;

    private Integer slotIntervalMinutes;

    private Boolean sameDayReservationAllowed;

    private Integer cancelDeadlineMinutes;

    /** 예약 슬롯 생성 시작 시간 */
    private LocalTime startTime;

    /** 예약 슬롯 생성 종료 시간 (이 시각 미만까지 슬롯 생성) */
    private LocalTime endTime;
}