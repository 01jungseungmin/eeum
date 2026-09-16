package com.eeum.eeum.application.category.dto.request;

import com.eeum.eeum.domain.category.enums.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "관리자 카테고리 생성 요청")
public class CategoryCreateRequestDto {

    @NotNull(message = "카테고리 타입은 필수입니다.")
    @Schema(description = "카테고리 타입", example = "COMMUNITY")
    private CategoryType type;

    @Schema(description = "상위 카테고리 ID. 최상위 카테고리는 생략", example = "1")
    private Long parentId;

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
    @Schema(description = "카테고리 이름", example = "동네 이야기")
    private String name;

    @NotNull(message = "표시 순서는 필수입니다.")
    @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
    @Schema(description = "같은 상위 카테고리 내 표시 순서", example = "0")
    private Integer displayOrder;
}
