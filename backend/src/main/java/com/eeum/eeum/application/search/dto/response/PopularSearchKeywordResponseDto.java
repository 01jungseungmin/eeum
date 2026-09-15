package com.eeum.eeum.application.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "인기 검색어")
public class PopularSearchKeywordResponseDto {

    @Schema(description = "순위", example = "1")
    private final int rank;

    @Schema(description = "정규화된 검색어", example = "치킨")
    private final String keyword;
}
