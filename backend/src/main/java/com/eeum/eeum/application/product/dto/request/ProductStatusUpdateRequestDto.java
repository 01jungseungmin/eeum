package com.eeum.eeum.application.product.dto.request;

import com.eeum.eeum.domain.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "상품 상태 변경 요청")
public class ProductStatusUpdateRequestDto {

    @Schema(
            description = "변경할 상품 상태",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "SOLD_OUT", "INACTIVE"}
    )
    @NotNull(message = "상품 상태는 필수입니다.")
    private ProductStatus status;
}