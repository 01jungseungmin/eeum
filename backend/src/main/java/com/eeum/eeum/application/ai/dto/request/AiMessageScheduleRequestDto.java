package com.eeum.eeum.application.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "예약 발송 요청")
public class AiMessageScheduleRequestDto {

    @NotNull
    @Schema(description = "예약 발송 시각 (현재보다 미래여야 함)", example = "2026-07-03T10:00:00")
    private LocalDateTime scheduledAt;
}
