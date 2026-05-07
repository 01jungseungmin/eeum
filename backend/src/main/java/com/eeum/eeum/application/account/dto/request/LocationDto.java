package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "GPS 위치 좌표")
public class LocationDto {

    @Schema(description = "위도", example = "37.5665")
    @NotNull(message = "위도는 필수입니다")
    private Double latitude;

    @Schema(description = "경도", example = "126.9780")
    @NotNull(message = "경도는 필수입니다")
    private Double longitude;
}
