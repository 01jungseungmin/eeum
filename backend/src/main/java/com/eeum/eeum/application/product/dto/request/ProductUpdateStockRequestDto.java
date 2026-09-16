package com.eeum.eeum.application.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "상품 재고 수정 요청")
public class ProductUpdateStockRequestDto {

    @Schema(description = "변경할 재고 수량", example = "15")
    private Integer stock;
}