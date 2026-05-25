package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Schema(description = "영업시간 입력 요청")
public class BusinessHourRequestDto {

    @Schema(description = "요일", example = "MONDAY")
    @NotNull(message = "요일은 필수입니다.")
    private DayOfWeek dayOfWeek;

    @Schema(description = "오픈 시간", example = "09:00")
    private LocalTime openTime;

    @Schema(description = "마감 시간", example = "20:00")
    private LocalTime closeTime;

    @Schema(description = "휴무 여부", example = "false")
    @NotNull(message = "휴무 여부는 필수입니다.")
    private Boolean closed;
}