package com.eeum.eeum.application.product.dto.request;

import com.eeum.eeum.domain.product.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

@Getter
@Schema(description = "상품 등록 요청")
public class ProductCreateRequestDto {

    @Schema(description = "상품 유형", example = "MENU")
    @NotNull(message = "상품 유형은 필수입니다.")
    private ProductType productType;

    @Schema(description = "상품명", example = "김치찌개")
    @NotBlank(message = "상품명은 필수입니다.")
    private String name;

    @Schema(description = "카테고리 ID", example = "1")
    @NotNull(message = "카테고리는 필수입니다.")
    private Long categoryId;

    @Schema(description = "기본 가격", example = "8000")
    @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.")
    private Integer basePrice;

    @Schema(description = "재고 수량", example = "10")
    @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
    private Integer stockQuantity;

    @Schema(description = "상품 설명", example = "직접 끓인 김치찌개입니다.")
    private String description;
}