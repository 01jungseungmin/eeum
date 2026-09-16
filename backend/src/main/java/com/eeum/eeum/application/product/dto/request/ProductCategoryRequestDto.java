package com.eeum.eeum.application.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

@Getter
@Schema(description = "상품 카테고리 요청")
public class ProductCategoryRequestDto {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Schema(description = "카테고리 이름", example = "대표 메뉴")
    private String name;

    @NotNull(message = "표시 순서는 필수입니다.")
    @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
    @Schema(description = "표시 순서", example = "0")
    private Integer displayOrder;
}