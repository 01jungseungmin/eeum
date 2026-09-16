package com.eeum.eeum.application.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "방문 예약 상태 변경 요청")
public class VisitReservationStatusUpdateRequestDto {

    @Schema(description = "거절 사유", example = "해당 시간에는 예약이 어렵습니다.")
    private String rejectReason;
}