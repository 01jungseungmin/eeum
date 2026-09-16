package com.eeum.eeum.application.region.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "지역 검색 응답")
public class RegionSearchResponseDto {

    @Schema(description = "지역 ID", example = "1")
    private Long regionId;

    @Schema(description = "행정동 코드", example = "4119710100")
    private String regionCode;

    @Schema(description = "시/도", example = "경기도")
    private String siDo;

    @Schema(description = "시/군/구", example = "부천시 오정구")
    private String gunGu;

    @Schema(description = "읍/면/동", example = "원종1동")
    private String dong;

    @Schema(description = "전체 지역명", example = "경기도 부천시 오정구 원종1동")
    private String fullName;

    @Schema(description = "위도", example = "37.525")
    private Double latitude;

    @Schema(description = "경도", example = "126.806")
    private Double longitude;

    @Schema(description = "현재 위치와의 거리 km", example = "1.25")
    private Double distance;
}