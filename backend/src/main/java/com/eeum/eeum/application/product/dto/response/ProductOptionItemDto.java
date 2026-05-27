package com.eeum.eeum.application.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "상품 옵션 항목 응답")
public class ProductOptionItemDto {

    @Schema(description = "옵션 항목 ID", example = "1")
    private Long itemId;

    @Schema(description = "옵션 항목 이름", example = "매운맛")
    private String itemName;

    @Schema(description = "추가 금액", example = "500")
    private BigDecimal additionalPrice;

    @Schema(description = "기본 선택 여부", example = "false")
    private boolean isDefault;

    @Schema(description = "표시 순서", example = "0")
    private Integer displayOrder;

    @Schema(description = "선택 가능 여부", example = "true")
    private boolean isAvailable;
}