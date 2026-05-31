package com.eeum.eeum.application.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
@Schema(description = "장바구니 상품 수량 변경 요청")
public class CartItemUpdateRequestDto {

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 1개 이상이어야 합니다.")
    @Schema(description = "수량", example = "3")
    private Integer quantity;
}