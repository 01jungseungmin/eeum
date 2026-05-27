package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.ProductCategoryRequestDto;
import com.eeum.eeum.application.product.dto.response.ProductCategoryResponseDto;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final StoreRepository storeRepository;
    private final ProductCategoryRepository productCategoryRepository;

    // 카테고리 목록 조회
    @Transactional(readOnly = true)
    public List<ProductCategoryResponseDto> getCategories(Long accountId) {
        Store store = getStore(accountId);

        return productCategoryRepository
                .findByStore_StoreIdOrderByDisplayOrderAsc(store.getStoreId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    // 기본 카테고리 생성
    @Transactional
    public void createDefaultCategories(Store store) {
        if (productCategoryRepository.existsByStore_StoreId(store.getStoreId())) {
            return;
        }

        List<ProductCategory> categories = List.of(
                ProductCategory.create(store, "대표 메뉴", 0),
                ProductCategory.create(store, "일반 상품", 1),
                ProductCategory.create(store, "기타", 2)
        );

        productCategoryRepository.saveAll(categories);
    }

    // 카테고리 생성
    @Transactional
    public ProductCategoryResponseDto createCategory(Long accountId, ProductCategoryRequestDto request) {
        Store store = getStore(accountId);

        validateDuplicateCategoryName(store.getStoreId(), request.getName());

        ProductCategory category = ProductCategory.create(store, request.getName(), request.getDisplayOrder());

        productCategoryRepository.save(category);

        log.info("상품 카테고리 생성: accountId={}, storeId={}, categoryId={}", accountId, store.getStoreId(), category.getProductCategoryId());

        return toDto(category);
    }

    // 카테고리 수정
    @Transactional
    public ProductCategoryResponseDto updateCategory(Long accountId, Long categoryId,
            ProductCategoryRequestDto request) {
        ProductCategory category = getCategoryWithOwnerCheck(accountId, categoryId);

        validateDuplicateCategoryNameForUpdate(category.getStore().getStoreId(), request.getName(), categoryId);
        category.update(request.getName(), request.getDisplayOrder());

        log.info("상품 카테고리 수정: accountId={}, categoryId={}",accountId, categoryId);
        return toDto(category);
    }

    // 카테고리 삭제 (소속 상품 있으면 비활성화)
    @Transactional
    public void deleteCategory(Long accountId, Long categoryId) {
        ProductCategory category = getCategoryWithOwnerCheck(accountId, categoryId);
        int productCount = productCategoryRepository.countProductsByCategoryId(categoryId);

        if (productCount > 0) {
            // 소속 상품이 있으면 비활성화만
            category.deactivate();
            log.info("상품 카테고리 비활성화: accountId={}, categoryId={}, productCount={}",accountId, categoryId, productCount);
            return;
        }
        productCategoryRepository.delete(category);
        log.info("상품 카테고리 삭제: accountId={}, categoryId={}", accountId, categoryId);
    }

    // 카테고리 활성화
    @Transactional
    public void activateCategory(Long accountId, Long categoryId) {
        ProductCategory category = getCategoryWithOwnerCheck(accountId, categoryId);
        category.activate();
        log.info("상품 카테고리 활성화: accountId={}, categoryId={}", accountId, categoryId);
    }

    // 카테고리 비활성화
    @Transactional
    public void deactivateCategory(Long accountId, Long categoryId) {
        ProductCategory category = getCategoryWithOwnerCheck(accountId, categoryId);
        category.deactivate();
        log.info("상품 카테고리 비활성화: accountId={}, categoryId={}", accountId, categoryId);

    }

    // ===================== 내부 유틸 =====================

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }


    private void validateDuplicateCategoryName(Long storeId, String name) {
        if (productCategoryRepository.existsByStore_StoreIdAndName(storeId, name)) {
            throw new BusinessException(ErrorCode.COMMON_DUPLICATE_RESOURCE);
        }
    }

    private void validateDuplicateCategoryNameForUpdate(Long storeId, String name, Long categoryId) {
        if (productCategoryRepository.existsByStore_StoreIdAndNameAndProductCategoryIdNot(storeId, name, categoryId)) {
            throw new BusinessException(ErrorCode.COMMON_DUPLICATE_RESOURCE);
        }
    }

    private ProductCategory getCategoryWithOwnerCheck(Long accountId, Long categoryId) {
        ProductCategory category = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.getStore().getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return category;
    }

    private ProductCategoryResponseDto toDto(ProductCategory category) {
        int productCount = productCategoryRepository
                .countProductsByCategoryId(category.getProductCategoryId());
        return ProductCategoryResponseDto.builder()
                .productCategoryId(category.getProductCategoryId())
                .storeId(category.getStore().getStoreId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.isActive())
                .productCount(productCount)
                .build();
    }
}