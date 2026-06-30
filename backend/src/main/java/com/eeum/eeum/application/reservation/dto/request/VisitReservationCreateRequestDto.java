package com.eeum.eeum.application.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Schema(description = "매장 방문 예약 생성 요청")
public class VisitReservationCreateRequestDto {

    @NotNull(message = "방문 날짜는 필수입니다.")
    @FutureOrPresent(message = "방문 날짜는 오늘 이후여야 합니다.")
    @Schema(description = "방문 날짜", example = "2026-06-10")
    private LocalDate visitDate;

    @NotNull(message = "방문 시간은 필수입니다.")
    @Schema(description = "방문 시간", example = "14:30")
    private LocalTime visitTime;

    @Min(value = 1, message = "방문 인원은 1명 이상이어야 합니다.")
    @Schema(description = "방문 인원 (테이블 자동 배정 기준)", example = "3", defaultValue = "1")
    private Integer visitorCount;

    @Schema(description = "요청사항", example = "창가 자리 가능하면 부탁드립니다.")
    private String requestMessage;
}