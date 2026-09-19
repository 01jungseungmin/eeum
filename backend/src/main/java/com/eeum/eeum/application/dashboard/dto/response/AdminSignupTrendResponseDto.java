package com.eeum.eeum.application.dashboard.dto.response;

import com.eeum.eeum.application.dashboard.enums.SignupMemberType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@Schema(description = "관리자 대시보드 일자별 가입자 추이")
public class AdminSignupTrendResponseDto {

    @Schema(description = "가입자 구분", example = "GENERAL")
    private final SignupMemberType type;

    @Schema(description = "집계 시작일(포함)", example = "2026-09-13")
    private final LocalDate from;

    @Schema(description = "집계 종료일(포함, 오늘)", example = "2026-09-19")
    private final LocalDate to;

    @Schema(description = "기간 내 가입자 합계", example = "2700")
    private final long total;

    @Schema(description = "일자별 가입자 수. 오래된 날짜부터, 가입자가 없는 날도 0으로 포함")
    private final List<Daily> daily;

    @Getter
    @Builder
    @Schema(description = "하루 가입자 수")
    public static class Daily {

        @Schema(description = "날짜", example = "2026-09-19")
        private final LocalDate date;

        @Schema(description = "요일", example = "SATURDAY")
        private final DayOfWeek dayOfWeek;

        @Schema(description = "가입자 수", example = "500")
        private final long count;
    }
}
