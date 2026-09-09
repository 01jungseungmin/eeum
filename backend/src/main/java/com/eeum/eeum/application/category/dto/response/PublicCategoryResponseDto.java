package com.eeum.eeum.application.category.dto.response;

import com.eeum.eeum.domain.category.enums.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "사용자용 카테고리 응답")
public class PublicCategoryResponseDto {

    @Schema(description = "카테고리 ID", example = "13")
    private Long categoryId;

    @Schema(description = "카테고리 유형", example = "USED")
    private CategoryType type;

    @Schema(description = "상위 카테고리 ID. 최상위 카테고리면 null")
    private Long parentId;

    @Schema(description = "카테고리명", example = "디지털/가전")
    private String name;

    @Schema(description = "표시 순서", example = "1")
    private Integer displayOrder;

    @Schema(description = "카테고리 깊이", example = "1")
    private Integer depth;
}
