package com.eeum.eeum.application.favorite.dto.request;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "다수 대상 찜 여부 일괄 조회 요청")
public class FavoriteBatchCheckRequestDto {

    @NotNull(message = "찜 대상 타입은 필수입니다.")
    @Schema(description = "찜 대상 타입", example = "STORE")
    private FavoriteRefType refType;

    @NotEmpty(message = "조회할 ID 목록은 1개 이상이어야 합니다.")
    @Size(max = 100, message = "한 번에 최대 100개까지 조회할 수 있습니다.")
    @Schema(description = "조회할 대상 ID 목록", example = "[1, 2, 3]")
    private List<@NotNull @Positive Long> refIds;
}