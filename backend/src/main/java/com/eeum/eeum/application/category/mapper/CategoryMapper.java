package com.eeum.eeum.application.category.mapper;

import com.eeum.eeum.application.category.dto.response.CategoryResponseDto;
import com.eeum.eeum.domain.category.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponseDto toResponseDto(Category category) {
        return CategoryResponseDto.builder()
                .categoryId(category.getCategoryId())
                .type(category.getType())
                .parentId(category.getParentId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .depth(category.getDepth())
                .isActive(category.isActive())
                .createdAt(category.getCreatedAt())
                .modifiedAt(category.getModifiedAt())
                .build();
    }
}
