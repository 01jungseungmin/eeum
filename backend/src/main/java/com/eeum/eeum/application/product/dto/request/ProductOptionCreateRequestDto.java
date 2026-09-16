package com.eeum.eeum.application.product.dto.request;

import com.eeum.eeum.domain.product.enums.OptionSelectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Schema(description = "옵션 그룹 생성 요청")
public class ProductOptionCreateRequestDto {

    @NotBlank(message = "옵션 그룹 이름은 필수입니다.")
    @Schema(description = "옵션 그룹 이름", example = "맵기 선택")
    private String groupName;

    @NotNull(message = "옵션 선택 방식은 필수입니다.")
    @Schema(description = "옵션 선택 방식", example = "SINGLE")
    private OptionSelectionType selectionType;

    @NotNull(message = "필수 선택 여부는 필수입니다.")
    @Schema(description = "필수 선택 여부", example = "true")
    private Boolean isRequired;

    @NotNull(message = "표시 순서는 필수입니다.")
    @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
    @Schema(description = "표시 순서", example = "0")
    private Integer displayOrder;

    @Valid
    @NotEmpty(message = "옵션 항목은 1개 이상 필요합니다.")
    @Schema(description = "옵션 항목 목록")
    private List<ProductOptionItemCreateDto> items;

    @Getter
    @Schema(description = "옵션 항목 생성 요청")
    public static class ProductOptionItemCreateDto {

        @NotBlank(message = "옵션 항목 이름은 필수입니다.")
        @Schema(description = "옵션 항목 이름", example = "매운맛")
        private String itemName;

        @NotNull(message = "추가 금액은 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = true, message = "추가 금액은 0 이상이어야 합니다.")
        @Schema(description = "추가 금액", example = "500")
        private BigDecimal additionalPrice;

        @Schema(description = "기본 선택 여부", example = "false")
        private boolean isDefault = false;

        @NotNull(message = "표시 순서는 필수입니다.")
        @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
        @Schema(description = "표시 순서", example = "0")
        private Integer displayOrder;
    }
}