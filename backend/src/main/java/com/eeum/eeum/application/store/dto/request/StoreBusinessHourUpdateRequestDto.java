package com.eeum.eeum.application.store.dto.request;

import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalTime;
import java.util.List;

@Getter
@Schema(description = "상점 요일별 영업시간 수정 요청")
public class StoreBusinessHourUpdateRequestDto {

    @Valid
    @NotEmpty(message = "영업시간은 최소 1개 이상 필요합니다.")
    @Schema(description = "요일별 영업시간 목록")
    private List<BusinessHourItem> businessHours;

    @Getter
    public static class BusinessHourItem {

        @NotNull(message = "요일은 필수입니다.")
        @Schema(description = "요일", example = "MONDAY")
        private StoreDayOfWeek dayOfWeek;

        @Schema(description = "휴무 여부", example = "false")
        private boolean closed;

        @Schema(description = "오픈 시간", example = "09:00")
        private LocalTime openTime;

        @Schema(description = "마감 시간", example = "19:00")
        private LocalTime closeTime;
    }
}