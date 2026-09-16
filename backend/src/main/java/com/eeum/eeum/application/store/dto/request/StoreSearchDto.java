package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "상점 검색 조건")
public class StoreSearchDto {

    @Schema(description = "상점 업종 카테고리 ID 목록")
    private List<Long> categoryIds;

    @Schema(description = "지역 ID", example = "1")
    private Long regionId;

    @Schema(description = "검색 키워드", example = "카페")
    private String keyword;
}
