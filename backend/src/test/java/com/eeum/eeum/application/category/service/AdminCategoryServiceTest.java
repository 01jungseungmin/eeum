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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    private AdminCategoryService adminCategoryService;

    @Mock private CategoryRepository categoryRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private CommunityPostRepository communityPostRepository;

    @BeforeEach
    void setUp() {
        adminCategoryService = new AdminCategoryService(
                categoryRepository,
                storeRepository,
                communityPostRepository,
                new CategoryMapper()
        );
    }

    @Test
    void 타입별_카테고리_목록은_비활성을_포함해_조회한다() {
        Category active = category(1L, CategoryType.COMMUNITY, null, "동네 이야기", 0, 1);
        Category inactive = category(2L, CategoryType.COMMUNITY, null, "질문", 1, 1);
        inactive.deactivate();
        when(categoryRepository.findAllByTypeOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
                CategoryType.COMMUNITY)).thenReturn(List.of(active, inactive));

        List<CategoryResponseDto> result = adminCategoryService.getAllCategories(CategoryType.COMMUNITY);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryResponseDto::isActive).containsExactly(true, false);
    }

    @Test
    void 최상위_카테고리를_생성한다() {
        CategoryCreateRequestDto request = createRequest(
                CategoryType.COMMUNITY, null, "  동네 이야기  ", 0);
        when(categoryRepository.existsByTypeAndParentIdAndName(
                CategoryType.COMMUNITY, null, "동네 이야기")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "categoryId", 1L);
            return saved;
        });

        CategoryResponseDto result = adminCategoryService.createCategory(request);

        assertThat(result.getCategoryId()).isEqualTo(1L);
        assertThat(result.getParentId()).isNull();
        assertThat(result.getDepth()).isEqualTo(1);
        assertThat(result.getName()).isEqualTo("동네 이야기");
    }

    @Test
    void 하위_카테고리는_부모보다_한_단계_깊게_생성한다() {
        Category parent = category(1L, CategoryType.STORE, null, "음식점", 0, 1);
        CategoryCreateRequestDto request = createRequest(CategoryType.STORE, 1L, "한식", 0);
        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByTypeAndParentIdAndName(CategoryType.STORE, 1L, "한식"))
                .thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDto result = adminCategoryService.createCategory(request);

        assertThat(result.getParentId()).isEqualTo(1L);
        assertThat(result.getDepth()).isEqualTo(2);
    }

    @Test
    void 깊이_3인_카테고리에는_자식을_생성할_수_없다() {
        Category parent = category(3L, CategoryType.STORE, 2L, "한식", 0, 3);
        CategoryCreateRequestDto request = createRequest(CategoryType.STORE, 3L, "찌개", 0);
        when(categoryRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> adminCategoryService.createCategory(request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void 같은_부모_아래_중복_이름은_생성할_수_없다() {
        CategoryCreateRequestDto request = createRequest(CategoryType.COMMUNITY, null, "질문", 0);
        when(categoryRepository.existsByTypeAndParentIdAndName(
                CategoryType.COMMUNITY, null, "질문")).thenReturn(true);

        assertThatThrownBy(() -> adminCategoryService.createCategory(request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_DUPLICATE);
    }

    @Test
    void 카테고리_이름과_표시_순서를_수정한다() {
        Category category = category(1L, CategoryType.COMMUNITY, null, "기존", 0, 1);
        CategoryUpdateRequestDto request = updateRequest(" 변경 ", 4);
        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByTypeAndParentIdAndNameAndCategoryIdNot(
                CategoryType.COMMUNITY, null, "변경", 1L)).thenReturn(false);

        CategoryResponseDto result = adminCategoryService.updateCategory(1L, request);

        assertThat(result.getName()).isEqualTo("변경");
        assertThat(result.getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void 형제_전체_ID_순서대로_표시_순서를_재배치한다() {
        Category first = category(1L, CategoryType.COMMUNITY, null, "첫째", 0, 1);
        Category second = category(2L, CategoryType.COMMUNITY, null, "둘째", 1, 1);
        CategoryOrderUpdateRequestDto request = orderRequest(
                CategoryType.COMMUNITY, null, List.of(2L, 1L));
        when(categoryRepository.findSiblingsForUpdate(CategoryType.COMMUNITY, null))
                .thenReturn(List.of(first, second));

        adminCategoryService.updateCategoryOrder(request);

        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void 순서_변경_ID가_중복되거나_누락되면_거부한다() {
        Category first = category(1L, CategoryType.COMMUNITY, null, "첫째", 0, 1);
        Category second = category(2L, CategoryType.COMMUNITY, null, "둘째", 1, 1);
        CategoryOrderUpdateRequestDto request = orderRequest(
                CategoryType.COMMUNITY, null, List.of(1L, 1L));
        when(categoryRepository.findSiblingsForUpdate(CategoryType.COMMUNITY, null))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> adminCategoryService.updateCategoryOrder(request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_INVALID_ORDER);
    }

    @Test
    void 참조_중인_카테고리는_물리_삭제할_수_없다() {
        Category category = category(1L, CategoryType.STORE, null, "음식점", 0, 1);
        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(storeRepository.existsByCategory_CategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> adminCategoryService.hardDeleteCategory(1L))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_IN_USE);

        verify(categoryRepository, never()).delete(any());
        verify(communityPostRepository, never()).existsByCategory_CategoryId(any());
    }

    @Test
    void 자식과_참조가_없는_카테고리는_물리_삭제한다() {
        Category category = category(1L, CategoryType.USED, null, "디지털", 0, 1);
        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(storeRepository.existsByCategory_CategoryId(1L)).thenReturn(false);
        when(communityPostRepository.existsByCategory_CategoryId(1L)).thenReturn(false);

        adminCategoryService.hardDeleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    private Category category(
            Long id,
            CategoryType type,
            Long parentId,
            String name,
            int displayOrder,
            int depth
    ) {
        Category category = parentId == null
                ? Category.createRoot(type, name, displayOrder)
                : Category.createChild(type, parentId, name, displayOrder, depth);
        ReflectionTestUtils.setField(category, "categoryId", id);
        if (parentId == null && depth != 1) {
            ReflectionTestUtils.setField(category, "depth", depth);
        }
        return category;
    }

    private CategoryCreateRequestDto createRequest(
            CategoryType type,
            Long parentId,
            String name,
            int displayOrder
    ) {
        CategoryCreateRequestDto request = new CategoryCreateRequestDto();
        ReflectionTestUtils.setField(request, "type", type);
        ReflectionTestUtils.setField(request, "parentId", parentId);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "displayOrder", displayOrder);
        return request;
    }

    private CategoryUpdateRequestDto updateRequest(String name, int displayOrder) {
        CategoryUpdateRequestDto request = new CategoryUpdateRequestDto();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "displayOrder", displayOrder);
        return request;
    }

    private CategoryOrderUpdateRequestDto orderRequest(
            CategoryType type,
            Long parentId,
            List<Long> categoryIds
    ) {
        CategoryOrderUpdateRequestDto request = new CategoryOrderUpdateRequestDto();
        ReflectionTestUtils.setField(request, "type", type);
        ReflectionTestUtils.setField(request, "parentId", parentId);
        ReflectionTestUtils.setField(request, "categoryIds", categoryIds);
        return request;
    }
}
