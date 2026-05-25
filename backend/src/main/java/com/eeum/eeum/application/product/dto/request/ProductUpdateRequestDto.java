package com.eeum.eeum.application.product.dto.request;

import com.eeum.eeum.domain.product.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "상품 수정 요청")
public class ProductUpdateRequestDto {

    @Schema(description = "상품 유형", example = "SALE", allowableValues = {"SALE", "RESERVATION", "MENU"})
    @NotNull(message = "상품 유형은 필수입니다.")
    private ProductType productType;

    @Schema(description = "상품명", example = "김치찌개 반찬 세트")
    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 100, message = "상품명은 100자 이하로 입력해야 합니다.")
    private String name;

    @Schema(description = "상품 설명", example = "직접 끓인 김치찌개와 반찬 세트입니다.")
    @Size(max = 1000, message = "상품 설명은 1000자 이하로 입력해야 합니다.")
    private String description;

    @Schema(description = "기본 가격", example = "9000")
    @NotNull(message = "기본 가격은 필수입니다.")
    @PositiveOrZero(message = "기본 가격은 0원 이상이어야 합니다.")
    private Integer basePrice;

    @Schema(description = "재고 수량", example = "15")
    @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
    private Integer stockQuantity;

    @Schema(description = "예약 가능 인원. 예약 상품에서 사용합니다.", example = "4")
    @PositiveOrZero(message = "예약 가능 인원은 0 이상이어야 합니다.")
    private Integer reservationCapacity;

    @Schema(description = "상품 카테고리 ID", example = "1")
    private Long categoryId;
}