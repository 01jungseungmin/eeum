package com.eeum.eeum.application.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Schema(description = "날짜별 방문 예약 시간대 설정 저장 요청")
public class VisitReservationTimeSlotUpdateRequestDto {

    @NotNull(message = "설정 날짜는 필수입니다.")
    @Schema(description = "설정 날짜", example = "2026-04-29")
    private LocalDate date;

    @Valid
    @NotEmpty(message = "시간대 설정 목록은 비어 있을 수 없습니다.")
    @Schema(description = "시간대별 설정 목록")
    private List<VisitReservationTimeSlotItemRequestDto> slots;
}