package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "찜 항목 응답 (내 찜 목록)")
public class FavoriteResponseDto {

    @Schema(description = "찜 ID", example = "10")
    private Long favoriteId;

    @Schema(description = "찜 대상 타입", example = "STORE")
    private FavoriteRefType refType;

    @Schema(description = "찜 대상 ID", example = "3")
    private Long refId;

    @Schema(description = "찜 등록 일시")
    private LocalDateTime createdAt;

    public static FavoriteResponseDto from(Favorite favorite) {
        return FavoriteResponseDto.builder()
                .favoriteId(favorite.getFavoriteId())
                .refType(favorite.getRefType())
                .refId(favorite.getRefId())
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}