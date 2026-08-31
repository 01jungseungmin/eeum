package com.eeum.eeum.application.category.dto.request;

import com.eeum.eeum.domain.category.enums.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "관리자 카테고리 순서 일괄 변경 요청")
public class CategoryOrderUpdateRequestDto {

    @NotNull(message = "카테고리 타입은 필수입니다.")
    @Schema(description = "카테고리 타입", example = "COMMUNITY")
    private CategoryType type;

    @Schema(description = "상위 카테고리 ID. 최상위 카테고리 순서 변경 시 생략", example = "1")
    private Long parentId;

    @NotEmpty(message = "정렬할 카테고리 ID 목록은 필수입니다.")
    @Schema(description = "원하는 표시 순서대로 나열한 같은 부모의 전체 카테고리 ID", example = "[3, 1, 2]")
    private List<@NotNull(message = "카테고리 ID는 null일 수 없습니다.") Long> categoryIds;
}
