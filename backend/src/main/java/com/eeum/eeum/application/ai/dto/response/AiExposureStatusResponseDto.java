package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "생활권 매칭 노출 상태")
public class AiExposureStatusResponseDto {

    @Schema(description = "노출 진행 여부", example = "true")
    private final boolean active;

    @Schema(description = "노출 시작 시각")
    private final LocalDateTime startedAt;

    @Schema(description = "노출 중지 시각")
    private final LocalDateTime stoppedAt;

    @Schema(description = "노출 중 대상 수", example = "34")
    private final int targetCount;

    @Schema(description = "반경 (km)", example = "1.5")
    private final double radiusKm;

    @Schema(description = "관심사", example = "한식")
    private final String interest;

    @Schema(description = "고객 유형", example = "ALL")
    private final AiCustomerType customerType;

    public static AiExposureStatusResponseDto from(AiExposureStatus status) {
        return AiExposureStatusResponseDto.builder()
                .active(status.isActive())
                .startedAt(status.getStartedAt())
                .stoppedAt(status.getStoppedAt())
                .targetCount(status.getTargetCount())
                .radiusKm(status.getRadiusKm())
                .interest(status.getInterest())
                .customerType(status.getCustomerType())
                .build();
    }
}
