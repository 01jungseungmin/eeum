package com.eeum.eeum.application.category.service;

import com.eeum.eeum.application.category.dto.response.PublicCategoryResponseDto;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.exception.NotFoundException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void 활성_카테고리만_사용자_필터_형식으로_조회한다() {
        // given
        CategoryQueryService service = new CategoryQueryService(categoryRepository);
        Category root = category(13L, CategoryType.USED, null, "디지털/가전", 1, 1);
        Category child = category(130L, CategoryType.USED, 13L, "휴대폰/태블릿", 1, 2);
        when(categoryRepository.findAllByTypeAndIsActiveTrueOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
                CategoryType.USED)).thenReturn(List.of(root, child));

        // when
        List<PublicCategoryResponseDto> result = service.getActiveCategories(CategoryType.USED);

        // then
        assertThat(result).extracting(PublicCategoryResponseDto::getCategoryId)
                .containsExactly(13L, 130L);
        assertThat(result.get(1).getParentId()).isEqualTo(13L);
    }

    @Test
    void 상위_카테고리_선택은_활성_하위_카테고리를_함께_반환한다() {
        // given
        CategoryQueryService service = new CategoryQueryService(categoryRepository);
        Category root = category(13L, CategoryType.USED, null, "디지털/가전", 1, 1);
        Category child = category(130L, CategoryType.USED, 13L, "휴대폰/태블릿", 1, 2);
        Category grandchild = category(131L, CategoryType.USED, 130L, "안드로이드", 1, 3);
        when(categoryRepository.findAllByTypeAndIsActiveTrueOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
                CategoryType.USED)).thenReturn(List.of(root, child, grandchild));

        // when
        List<Long> result = service.resolveActiveCategoryIds(CategoryType.USED, 13L);

        // then
        assertThat(result).containsExactly(13L, 130L, 131L);
    }

    @Test
    void 비활성_또는_다른_유형_카테고리는_필터로_사용할_수_없다() {
        // given
        CategoryQueryService service = new CategoryQueryService(categoryRepository);
        when(categoryRepository.findAllByTypeAndIsActiveTrueOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
                CategoryType.STORE)).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> service.resolveActiveCategoryIds(CategoryType.STORE, 13L))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    private Category category(
            Long categoryId,
            CategoryType type,
            Long parentId,
            String name,
            int displayOrder,
            int depth
    ) {
        Category category = parentId == null
                ? Category.createRoot(type, name, displayOrder)
                : Category.createChild(type, parentId, name, displayOrder, depth);
        ReflectionTestUtils.setField(category, "categoryId", categoryId);
        return category;
    }
}
