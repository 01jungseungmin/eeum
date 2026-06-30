package com.eeum.eeum.application.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
@Schema(description = "사장용 날짜별 시간대 예약 현황")
public class VisitReservationLeftTimeSlotResponseDto {

    @Schema(description = "슬롯 시간")
    private LocalTime time;

    @Schema(description = "예약 가능 여부 (활성 테이블이 1개 이상 남아 있으면 true)")
    private boolean available;

    @Schema(description = "예약 가능한 테이블 수")
    private int availableTableCount;

    @Schema(description = "전체 활성 테이블 수")
    private int totalTableCount;

    @Schema(description = "예약된 테이블 수")
    private int reservedTableCount;

    @Schema(description = "예약 가능한 테이블 중 최소 수용 인원 (테이블 없으면 null)")
    private Integer minAvailableCapacity;

    @Schema(description = "예약 가능한 테이블 중 최대 수용 인원 (테이블 없으면 null)")
    private Integer maxAvailableCapacity;
}
