package com.eeum.eeum.application.owner.dto.response;

import com.eeum.eeum.domain.order.repository.ProductSalesQuantity;
import com.eeum.eeum.domain.product.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "상품별 판매 수량")
public class OwnerProductSalesResponseDto {

    @Schema(description = "상품 ID", example = "12")
    private final Long productId;

    @Schema(description = "상품명", example = "아메리카노")
    private final String productName;

    @Schema(description = "상품 유형", example = "MENU")
    private final ProductType productType;

    @Schema(description = "판매 수량 합계. 이벤트 상품 판매분 포함", example = "137")
    private final Long soldQuantity;

    public static OwnerProductSalesResponseDto from(ProductSalesQuantity stat) {
        return OwnerProductSalesResponseDto.builder()
                .productId(stat.productId())
                .productName(stat.productName())
                .productType(stat.productType())
                .soldQuantity(stat.soldQuantity())
                .build();
    }
}
