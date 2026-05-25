package com.eeum.eeum.application.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

@Getter
@Schema(description = "상품 재고 수정 요청")
public class ProductUpdateStockRequestDto {

    @Schema(description = "변경할 재고 수량", example = "15")
    @NotNull(message = "재고 수량은 필수입니다.")
    @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
    private Integer stock;
}