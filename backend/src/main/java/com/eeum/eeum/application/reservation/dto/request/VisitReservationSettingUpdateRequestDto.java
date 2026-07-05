package com.eeum.eeum.application.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Schema(description = "방문 예약 기능 설정 수정 요청")
public class VisitReservationSettingUpdateRequestDto {

    @NotNull(message = "방문 예약 기능 사용 여부는 필수입니다.")
    @Schema(description = "방문 예약 기능 사용 여부", example = "true")
    private Boolean enabled;

    @NotNull(message = "예약 시간 간격은 필수입니다.")
    @Min(value = 10, message = "예약 시간 간격은 10분 이상이어야 합니다.")
    @Schema(description = "예약 시간 간격(분)", example = "30")
    private Integer slotIntervalMinutes;

    @NotNull(message = "당일 예약 가능 여부는 필수입니다.")
    @Schema(description = "당일 예약 가능 여부", example = "true")
    private Boolean sameDayReservationAllowed;

    @NotNull(message = "예약 취소 가능 시간은 필수입니다.")
    @Min(value = 0, message = "예약 취소 가능 시간은 0분 이상이어야 합니다.")
    @Schema(description = "예약 몇 분 전까지 취소 가능한지", example = "30")
    private Integer cancelDeadlineMinutes;

    @NotNull(message = "예약 가능 시작 시간은 필수입니다.")
    @Schema(description = "예약 슬롯 생성 시작 시간", example = "09:00")
    private LocalTime startTime;

    @NotNull(message = "예약 가능 종료 시간은 필수입니다.")
    @Schema(description = "예약 슬롯 생성 종료 시간 (이 시각 미만까지 슬롯 생성)", example = "18:00")
    private LocalTime endTime;

}