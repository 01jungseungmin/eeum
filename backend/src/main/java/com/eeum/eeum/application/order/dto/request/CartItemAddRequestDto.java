package com.eeum.eeum.application.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "장바구니 상품 추가 요청")
public class CartItemAddRequestDto {

    @Schema(description = "일반 상품 ID", example = "1")
    private Long productId;

    @Schema(description = "이벤트 상품 ID", example = "1")
    private Long eventProductId;

    @Schema(description = "선택한 옵션 항목 ID 목록", example = "[1, 3]")
    private List<Long> selectedOptionItemIds;

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 1개 이상이어야 합니다.")
    @Schema(description = "수량", example = "2")
    private Integer quantity;
}