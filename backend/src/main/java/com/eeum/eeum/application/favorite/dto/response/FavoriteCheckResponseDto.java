package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "찜 여부 확인 응답")
public class FavoriteCheckResponseDto {

    @Schema(description = "찜 대상 타입", example = "STORE")
    private FavoriteRefType refType;

    @Schema(description = "찜 대상 ID", example = "3")
    private Long refId;

    @Schema(description = "찜 여부", example = "true")
    private boolean favorited;

    @Schema(description = "찜 ID. 단건 조회에서 찜한 경우에만 채워진다. "
            + "배치 조회(/favorites/check-batch)는 성능상 조회하지 않으므로 항상 null이다.",
            example = "10")
    private Long favoriteId;

    public static FavoriteCheckResponseDto of(FavoriteRefType refType, Long refId,
            boolean favorited, Long favoriteId) {
        return FavoriteCheckResponseDto.builder()
                .refType(refType)
                .refId(refId)
                .favorited(favorited)
                .favoriteId(favoriteId)
                .build();
    }
}