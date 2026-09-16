package com.eeum.eeum.application.product.dto.response;

import com.eeum.eeum.domain.product.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상품 생성 후 상품 정보")
public class ProductCreateResponseDto {

    @Schema(description = "상품 ID", example = "1")
    private Long productId;
}