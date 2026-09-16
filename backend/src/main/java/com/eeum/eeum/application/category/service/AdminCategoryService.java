package com.eeum.eeum.application.category.service;

import com.eeum.eeum.application.category.dto.request.CategoryCreateRequestDto;
import com.eeum.eeum.application.category.dto.request.CategoryOrderUpdateRequestDto;
import com.eeum.eeum.application.category.dto.request.CategoryUpdateRequestDto;
import com.eeum.eeum.application.category.dto.response.CategoryResponseDto;
import com.eeum.eeum.application.category.mapper.CategoryMapper;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getAllCategories(CategoryType type) {
        List<Category> categories = type == null
                ? categoryRepository.findAllByOrderByTypeAscDepthAscParentIdAscDisplayOrderAscCategoryIdAsc()
                : categoryRepository.findAllByTypeOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(type);

        return categories.stream()
                .map(categoryMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public CategoryResponseDto createCategory(CategoryCreateRequestDto request) {
        String name = normalizeName(request.getName());
        Long parentId = request.getParentId();
        int depth = 1;

        if (parentId != null) {
            // 부모 행을 잠근다. 잠그지 않으면 hardDeleteCategory가 "자식 없음"을 확인한 직후
            // 이 트랜잭션이 자식을 INSERT해 삭제된 부모를 가리키는 고아 카테고리가 남는다.
            // parent_id는 FK 없는 스칼라라 DB가 막아주지 못하므로 잠금이 유일한 방어선이다.
            Category parent = getCategoryForUpdateOrThrow(parentId);
            validateParent(parent, request.getType());
            depth = parent.getDepth() + 1;
        }

        validateDuplicate(request.getType(), parentId, name);

        Category category = parentId == null
                ? Category.createRoot(request.getType(), name, request.getDisplayOrder())
                : Category.createChild(request.getType(), parentId, name, request.getDisplayOrder(), depth);

        Category saved = saveWithDuplicateGuard(category);
        log.info("관리자 카테고리 생성: categoryId={}, type={}, parentId={}",
                saved.getCategoryId(), saved.getType(), saved.getParentId());
        return categoryMapper.toResponseDto(saved);
    }

    @Transactional
    public CategoryResponseDto updateCategory(Long categoryId, CategoryUpdateRequestDto request) {
        Category category = getCategoryForUpdateOrThrow(categoryId);
        String name = normalizeName(request.getName());

        if (categoryRepository.existsByTypeAndParentIdAndNameAndCategoryIdNot(
                category.getType(), category.getParentId(), name, categoryId)) {
            throw new ConflictException(ErrorCode.CATEGORY_DUPLICATE);
        }

        category.updateInfo(name, request.getDisplayOrder());
        flushWithDuplicateGuard();
        log.info("관리자 카테고리 수정: categoryId={}", categoryId);
        return categoryMapper.toResponseDto(category);
    }

    @Transactional
    public void activateCategory(Long categoryId) {
        Category category = getCategoryForUpdateOrThrow(categoryId);
        category.activate();
        log.info("관리자 카테고리 활성화: categoryId={}", categoryId);
    }

    @Transactional
    public void deactivateCategory(Long categoryId) {
        Category category = getCategoryForUpdateOrThrow(categoryId);
        category.deactivate();
        log.info("관리자 카테고리 비활성화: categoryId={}", categoryId);
    }

    @Transactional
    public void updateCategoryOrder(CategoryOrderUpdateRequestDto request) {
        List<Category> siblings = categoryRepository.findSiblingsForUpdate(
                request.getType(), request.getParentId());
        List<Long> requestedIds = request.getCategoryIds();
        Set<Long> requestedIdSet = new HashSet<>(requestedIds);
        Set<Long> siblingIds = siblings.stream()
                .map(Category::getCategoryId)
                .collect(Collectors.toSet());

        if (requestedIdSet.size() != requestedIds.size()
                || !requestedIdSet.equals(siblingIds)) {
            throw new BadRequestException(ErrorCode.CATEGORY_INVALID_ORDER);
        }

        Map<Long, Category> categoryById = siblings.stream()
                .collect(Collectors.toMap(Category::getCategoryId, category -> category));
        for (int displayOrder = 0; displayOrder < requestedIds.size(); displayOrder++) {
            categoryById.get(requestedIds.get(displayOrder)).updateDisplayOrder(displayOrder);
        }

        log.info("관리자 카테고리 순서 변경: type={}, parentId={}, categoryCount={}",
                request.getType(), request.getParentId(), requestedIds.size());
    }

    @Transactional
    public void hardDeleteCategory(Long categoryId) {
        Category category = categoryRepository.findByIdForUpdate(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND));

        if (categoryRepository.existsByParentId(categoryId)
                || storeRepository.existsByCategory_CategoryId(categoryId)
                || communityPostRepository.existsByCategory_CategoryId(categoryId)) {
            throw new BadRequestException(ErrorCode.CATEGORY_IN_USE);
        }

        categoryRepository.delete(category);
        log.info("관리자 카테고리 물리 삭제: categoryId={}", categoryId);
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // 모든 카테고리 변경은 대상 행을 잠근 뒤 수행한다 — hardDeleteCategory와 동일한 잠금 규칙.
    private Category getCategoryForUpdateOrThrow(Long categoryId) {
        return categoryRepository.findByIdForUpdate(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // exists 검사와 INSERT 사이에는 항상 틈이 있다. 최종 판정은 DB 유니크 제약이 하고,
    // 여기서는 그 제약 위반을 도메인 에러로 번역하기만 한다.
    private Category saveWithDuplicateGuard(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorCode.CATEGORY_DUPLICATE);
        }
    }

    private void flushWithDuplicateGuard() {
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorCode.CATEGORY_DUPLICATE);
        }
    }

    private void validateParent(Category parent, CategoryType childType) {
        if (parent.getType() != childType) {
            throw new BadRequestException(ErrorCode.CATEGORY_PARENT_TYPE_MISMATCH);
        }
        if (!parent.canHaveChild()) {
            throw new BadRequestException(ErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);
        }
    }

    private void validateDuplicate(CategoryType type, Long parentId, String name) {
        if (categoryRepository.existsByTypeAndParentIdAndName(type, parentId, name)) {
            throw new ConflictException(ErrorCode.CATEGORY_DUPLICATE);
        }
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}
