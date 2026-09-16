package com.eeum.eeum.application.product.dto.response;

import com.eeum.eeum.domain.product.enums.OptionSelectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "상품 옵션 그룹 응답")
public class ProductOptionDto {

    @Schema(description = "옵션 그룹 ID", example = "1")
    private Long optionId;

    @Schema(description = "옵션 그룹 이름", example = "맵기 선택")
    private String groupName;

    @Schema(description = "옵션 선택 방식", example = "SINGLE")
    private OptionSelectionType selectionType;

    @Schema(description = "필수 선택 여부", example = "true")
    private boolean isRequired;

    @Schema(description = "표시 순서", example = "0")
    private Integer displayOrder;

    @Schema(description = "옵션 선택 항목 목록")
    private List<ProductOptionItemDto> items;
}