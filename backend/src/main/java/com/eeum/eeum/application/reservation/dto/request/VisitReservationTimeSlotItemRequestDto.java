package com.eeum.eeum.application.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Schema(description = "방문 예약 시간대 설정 항목")
public class VisitReservationTimeSlotItemRequestDto {

    @NotNull(message = "예약 시간은 필수입니다.")
    @Schema(description = "예약 시간", example = "10:00")
    private LocalTime time;

    @NotNull(message = "최대 수용 인원은 필수입니다.")
    @Min(value = 1, message = "최대 수용 인원은 1명 이상이어야 합니다.")
    @Schema(description = "최대 수용 인원", example = "5")
    private Integer maxVisitorCount;

    @NotNull(message = "최대 팀 수는 필수입니다.")
    @Min(value = 1, message = "최대 팀 수는 1팀 이상이어야 합니다.")
    @Schema(description = "최대 팀 수", example = "2")
    private Integer maxTeamCount;

    @NotNull(message = "시간대 사용 여부는 필수입니다.")
    @Schema(description = "시간대 사용 여부", example = "true")
    private Boolean enabled;
}