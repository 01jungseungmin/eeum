package com.eeum.eeum.application.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "예약 가능 시간대 응답")
public class TimeSlotAvailabilityResponseDto {

    @Schema(description = "슬롯 시간")
    private LocalTime time;

    @Schema(description = "해당 시간대 예약 가능 여부")
    private boolean available;

    @Schema(description = "예약 가능한 테이블 수")
    private int availableTableCount;

    @Schema(description = "요청 인원 기준 예약 가능한 최소 좌석 수 (테이블이 없으면 null)")
    private Integer minAvailableCapacity;

    @Schema(description = "요청 인원 기준 예약 가능한 최대 좌석 수 (테이블이 없으면 null)")
    private Integer maxAvailableCapacity;

    @Schema(description = "좌석 수별 잔여 테이블 수")
    private List<TableAvailabilityResponseDto> tableAvailabilities;
}