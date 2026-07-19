package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "생활권 매칭 노출 가게")
public class AiExposedStoreDto {

    @Schema(description = "상점 ID")
    private final Long storeId;

    @Schema(description = "상점명")
    private final String storeName;

    @Schema(description = "주소")
    private final String address;

    @Schema(description = "노출 상태 ID — 클릭 로그 기록에 사용")
    private final Long exposureStatusId;

    @Schema(description = "관심사", example = "한식")
    private final String interest;

    @Schema(description = "요청 추적 ID — 클릭 시 함께 전달")
    private final String requestId;

    public static AiExposedStoreDto from(AiExposureStatus exposure, String requestId) {
        return AiExposedStoreDto.builder()
                .storeId(exposure.getStore().getStoreId())
                .storeName(exposure.getStore().getName())
                .address(exposure.getStore().getAddress())
                .exposureStatusId(exposure.getAiExposureStatusId())
                .interest(exposure.getInterest())
                .requestId(requestId)
                .build();
    }
}
