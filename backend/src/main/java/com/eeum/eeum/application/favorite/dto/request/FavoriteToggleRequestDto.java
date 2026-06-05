package com.eeum.eeum.application.favorite.dto.request;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "찜 토글 요청")
public class FavoriteToggleRequestDto {

    @NotNull(message = "찜 대상 타입은 필수입니다.")
    @Schema(description = "찜 대상 타입", example = "STORE", allowableValues = {"STORE", "USED_PRODUCT"})
    private FavoriteRefType refType;

    @NotNull(message = "찜 대상 ID는 필수입니다.")
    @Schema(description = "찜 대상 ID (storeId 또는 usedProductId)", example = "3")
    private Long refId;
}