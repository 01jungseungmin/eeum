package com.eeum.eeum.application.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "좌석 수별 테이블 예약 가능 현황")
public class TableAvailabilityResponseDto {

    @Schema(description = "테이블 수용 인원", example = "2")
    private Integer capacity;

    @Schema(description = "전체 활성 테이블 수", example = "5")
    private Integer totalCount;

    @Schema(description = "예약된 테이블 수", example = "2")
    private Integer reservedCount;

    @Schema(description = "잔여 테이블 수", example = "3")
    private Integer availableCount;
}