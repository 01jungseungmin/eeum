package com.eeum.eeum.application.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@Schema(description = "장바구니 응답")
public class CartResponseDto {

    @Schema(description = "장바구니 ID", example = "1")
    private Long cartId;

    @Schema(description = "상점 ID", example = "1")
    private Long storeId;

    @Schema(description = "상점명", example = "골드락 카페")
    private String storeName;

    @Schema(description = "장바구니 상품 목록")
    private List<CartItemResponseDto> items;

    @Schema(description = "총 상품 금액", example = "32200")
    private BigDecimal totalPrice;
}