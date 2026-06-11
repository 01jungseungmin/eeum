package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "찜 통계 응답 (관리자)")
public class FavoriteStatResponseDto {

    @Schema(description = "찜 대상 타입", example = "STORE")
    private FavoriteRefType refType;

    @Schema(description = "찜 대상 ID", example = "3")
    private Long refId;

    @Schema(description = "찜 수", example = "248")
    private Long favoriteCount;

    @Schema(description = "대상 이름 (Store면 상점명)", example = "승민 반찬가게")
    private String refName;

    public static FavoriteStatResponseDto of(FavoriteRefType refType, Long refId,
            Long favoriteCount, String refName) {
        return FavoriteStatResponseDto.builder()
                .refType(refType)
                .refId(refId)
                .favoriteCount(favoriteCount)
                .refName(refName)
                .build();
    }
}