package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "주변 상점 검색 조건")
public class NearbyStoreSearchCondition {

    @Schema(description = "현재 위도", example = "37.2636")
    private double latitude;

    @Schema(description = "현재 경도", example = "127.0286")
    private double longitude;

    @Schema(description = "검색 반경 km", example = "3.0")
    private double radiusKm = 3.0;

    @Schema(description = "상점 업종 카테고리 ID 목록")
    private List<Long> categoryIds;

    @Schema(description = "지역 ID", example = "1")
    private Long regionId;

    @Schema(description = "검색 키워드", example = "카페")
    private String keyword;
}
