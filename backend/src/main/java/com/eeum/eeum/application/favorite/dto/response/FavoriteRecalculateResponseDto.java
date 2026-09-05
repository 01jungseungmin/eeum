package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@Schema(description = "찜 카운트 정합성 재계산 결과 (관리자)")
public class FavoriteRecalculateResponseDto {

    @Schema(description = "대상 타입별 갱신된 행 수", example = "{\"STORE\": 120, \"USED_PRODUCT\": 3480}")
    private Map<FavoriteRefType, Integer> updatedRows;

    @Schema(description = "갱신된 전체 행 수", example = "3600")
    private int totalUpdated;

    public static FavoriteRecalculateResponseDto of(Map<FavoriteRefType, Integer> updatedRows) {
        return FavoriteRecalculateResponseDto.builder()
                .updatedRows(updatedRows)
                .totalUpdated(updatedRows.values().stream().mapToInt(Integer::intValue).sum())
                .build();
    }
}
