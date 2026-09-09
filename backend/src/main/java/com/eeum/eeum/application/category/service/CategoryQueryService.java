package com.eeum.eeum.application.category.service;

import com.eeum.eeum.application.category.dto.response.PublicCategoryResponseDto;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<PublicCategoryResponseDto> getActiveCategories(CategoryType type) {
        return getActiveCategoriesByType(type).stream()
                .map(this::toResponseDto)
                .toList();
    }

    /** 선택한 카테고리와 활성 하위 카테고리 ID를 모두 반환한다. */
    @Transactional(readOnly = true)
    public List<Long> resolveActiveCategoryIds(CategoryType type, Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        List<Category> categories = getActiveCategoriesByType(type);
        boolean selectedExists = categories.stream()
                .anyMatch(category -> category.getCategoryId().equals(categoryId));
        if (!selectedExists) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        Map<Long, List<Category>> childrenByParentId = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        List<Long> categoryIds = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long currentId = queue.removeFirst();
            categoryIds.add(currentId);
            childrenByParentId.getOrDefault(currentId, List.of()).stream()
                    .map(Category::getCategoryId)
                    .forEach(queue::addLast);
        }
        return categoryIds;
    }

    private List<Category> getActiveCategoriesByType(CategoryType type) {
        return categoryRepository
                .findAllByTypeAndIsActiveTrueOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(type);
    }

    private PublicCategoryResponseDto toResponseDto(Category category) {
        return PublicCategoryResponseDto.builder()
                .categoryId(category.getCategoryId())
                .type(category.getType())
                .parentId(category.getParentId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .depth(category.getDepth())
                .build();
    }
}
