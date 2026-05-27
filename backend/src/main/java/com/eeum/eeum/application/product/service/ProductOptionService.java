package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.ProductOptionCreateRequestDto;
import com.eeum.eeum.application.product.dto.request.ProductOptionUpdateRequestDto;
import com.eeum.eeum.application.product.dto.response.ProductOptionDto;
import com.eeum.eeum.application.product.dto.response.ProductOptionItemDto;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductOption;
import com.eeum.eeum.domain.product.entity.ProductOptionItem;
import com.eeum.eeum.domain.product.enums.OptionSelectionType;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductOptionItemRepository;
import com.eeum.eeum.domain.product.repository.ProductOptionRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
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
public class ProductOptionService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionItemRepository productOptionItemRepository;

    // 옵션 목록 조회
    @Transactional(readOnly = true)
    public List<ProductOptionDto> getOptions(Long accountId, Long productId) {
        getProductWithOwnerCheck(accountId, productId);
        return productOptionRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // 옵션 그룹 생성 (항목 포함)
    @Transactional
    public ProductOptionDto createOption(Long accountId, Long productId,
            ProductOptionCreateRequestDto request) {
        validateDefaultItem(request);

        Product product = getProductWithOwnerCheck(accountId, productId);
        validateOptionAvailableProduct(product);

        ProductOption option = ProductOption.create(
                product,
                request.getGroupName(),
                request.getSelectionType(),
                request.getIsRequired(),
                request.getDisplayOrder()
        );
        productOptionRepository.save(option);

        // 항목 저장
        List<ProductOptionItem> items = request.getItems().stream()
                .map(item -> ProductOptionItem.create(
                        option,
                        item.getItemName(),
                        item.getAdditionalPrice(),
                        item.isDefault(),
                        item.getDisplayOrder()
                ))
                .toList();
        productOptionItemRepository.saveAll(items);

        log.info("옵션 그룹 생성: productId={}, optionId={}", productId, option.getProductOptionId());
        return toDto(option);
    }

    // 옵션 그룹 전체 교체 (PUT)
    @Transactional
    public ProductOptionDto replaceOption(Long accountId, Long productId,
            Long optionId, ProductOptionUpdateRequestDto request) {

        validateDefaultItemForUpdate(request);

        getProductWithOwnerCheck(accountId, productId);
        ProductOption option = getOptionWithCheck(optionId, productId);

        // 기존 항목 전체 삭제 후 재생성
        productOptionItemRepository.deleteByProductOption_ProductOptionId(optionId);

        option.update(request.getGroupName(), request.getSelectionType(),
                request.getIsRequired(), request.getDisplayOrder());

        List<ProductOptionItem> items = request.getItems().stream()
                .map(item -> ProductOptionItem.create(
                        option,
                        item.getItemName(),
                        item.getAdditionalPrice(),
                        item.isDefault(),
                        item.getDisplayOrder()
                ))
                .toList();
        productOptionItemRepository.saveAll(items);

        log.info("옵션 그룹 전체 교체: optionId={}", optionId);
        return toDto(option);
    }

    // 옵션 그룹 삭제
    @Transactional
    public void deleteOption(Long accountId, Long productId, Long optionId) {
        getProductWithOwnerCheck(accountId, productId);
        ProductOption option = getOptionWithCheck(optionId, productId);

        productOptionItemRepository.deleteByProductOption_ProductOptionId(optionId);
        productOptionRepository.delete(option);

        log.info("옵션 그룹 삭제: optionId={}", optionId);
    }

    // 선택지 품절 토글
    @Transactional
    public void toggleItemAvailability(Long accountId, Long productId, Long itemId) {
        getProductWithOwnerCheck(accountId, productId);

        ProductOptionItem item = productOptionItemRepository
                .findByProductOptionItemIdAndProductOption_Product_ProductId(itemId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_OPTION_ITEM_NOT_FOUND));

        item.toggleAvailability();

        log.info("옵션 항목 품절 토글: itemId={}, isAvailable={}", itemId, item.isAvailable());
    }

    // ===================== 내부 유틸 =====================

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private void validateDefaultItem(ProductOptionCreateRequestDto request) {
        long defaultCount = request.getItems().stream()
                .filter(ProductOptionCreateRequestDto.ProductOptionItemCreateDto::isDefault)
                .count();

        if (request.getSelectionType() == OptionSelectionType.SINGLE && defaultCount > 1) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validateDefaultItemForUpdate(ProductOptionUpdateRequestDto request) {
        long defaultCount = request.getItems().stream()
                .filter(ProductOptionCreateRequestDto.ProductOptionItemCreateDto::isDefault)
                .count();

        if (request.getSelectionType() == OptionSelectionType.SINGLE && defaultCount > 1) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validateOptionAvailableProduct(Product product){
        if (product.getProductType() == ProductType.MENU) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private Product getProductWithOwnerCheck(Long accountId, Long productId) {
        Store store = getStore(accountId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return product;
    }


    private ProductOption getOptionWithCheck(Long optionId, Long productId) {
        return productOptionRepository
                .findByProductOptionIdAndProduct_ProductId(optionId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND));
    }

    private ProductOptionDto toDto(ProductOption option) {
        List<ProductOptionItemDto> items = productOptionItemRepository
                .findByProductOption_ProductOptionIdOrderByDisplayOrderAsc(
                        option.getProductOptionId())
                .stream()
                .map(item -> ProductOptionItemDto.builder()
                        .itemId(item.getProductOptionItemId())
                        .itemName(item.getItemName())
                        .additionalPrice(item.getAdditionalPrice())
                        .isDefault(item.isDefault())
                        .displayOrder(item.getDisplayOrder())
                        .isAvailable(item.isAvailable())
                        .build())
                .toList();

        return ProductOptionDto.builder()
                .optionId(option.getProductOptionId())
                .groupName(option.getGroupName())
                .selectionType(option.getSelectionType())
                .isRequired(option.isRequired())
                .displayOrder(option.getDisplayOrder())
                .items(items)
                .build();
    }
}