package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class StoreBusinessHourResponseDto {

    private StoreDayOfWeek dayOfWeek;
    private String dayLabel;
    private boolean closed;
    private LocalTime openTime;
    private LocalTime closeTime;
}