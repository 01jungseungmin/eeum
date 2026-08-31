package com.eeum.eeum.application.category.dto.response;

import com.eeum.eeum.domain.category.enums.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "관리자 카테고리 응답")
public class CategoryResponseDto {

    private Long categoryId;
    private CategoryType type;
    private Long parentId;
    private String name;
    private Integer displayOrder;
    private Integer depth;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
