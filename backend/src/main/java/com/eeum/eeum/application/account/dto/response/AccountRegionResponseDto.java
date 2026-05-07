package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "활동 지역 응답")
public class AccountRegionResponseDto {

    @Schema(description = "활동 지역 ID", example = "1")
    private Long accountRegionId;

    @Schema(description = "지역 ID", example = "1")
    private Long regionId;

    @Schema(description = "시/도", example = "서울특별시")
    private String siDo;

    @Schema(description = "시/군/구", example = "강남구")
    private String gunGu;

    @Schema(description = "행정동", example = "역삼동")
    private String dong;

    @Schema(description = "대표 지역 여부", example = "true")
    private Boolean isPrimary;

    @Schema(description = "GPS 인증 여부", example = "true")
    private Boolean verified;

    @Schema(description = "인증 일시", example = "2026-05-06T12:00:00")
    private LocalDateTime verifiedAt;

    @Schema(description = "등록 일시", example = "2026-05-06T12:00:00")
    private LocalDateTime createdAt;
}
