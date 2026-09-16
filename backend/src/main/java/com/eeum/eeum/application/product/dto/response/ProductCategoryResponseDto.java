package com.eeum.eeum.application.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "상품 카테고리 응답")
public class ProductCategoryResponseDto {

    @Schema(description = "상품 카테고리 ID", example = "1")
    private Long productCategoryId;

    @Schema(description = "상점 ID", example = "1")
    private Long storeId;

    @Schema(description = "카테고리 이름", example = "대표 메뉴")
    private String name;

    @Schema(description = "표시 순서", example = "0")
    private Integer displayOrder;

    @Schema(description = "활성화 여부", example = "true")
    private boolean isActive;

    @Schema(description = "소속 상품 수", example = "3")
    private Integer productCount;
}