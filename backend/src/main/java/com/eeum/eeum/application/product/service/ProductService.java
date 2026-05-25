package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.*;
import com.eeum.eeum.application.product.dto.response.ProductResponseDto;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getMyProducts(Long accountId) {
        Store store = getStore(accountId);
        return productRepository.findByStore_StoreId(store.getStoreId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long accountId, Long productId) {
        return toDto(getProductWithOwnerCheck(accountId, productId));
    }

    @Transactional
    public void createProduct(Long accountId, ProductCreateRequestDto request) {
        Store store = getStore(accountId);

        BigDecimal price = request.getBasePrice() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(request.getBasePrice());

        String description = request.getDescription() == null
                ? "" : request.getDescription();

        Product product = Product.create(
                store, request.getName(), description,
                price, request.getStockQuantity(), request.getProductType()
        );

        productRepository.save(product);
        log.info("상품 등록: accountId={}, productType={}", accountId, request.getProductType());
    }

    @Transactional
    public ProductResponseDto updateProduct(Long accountId, Long productId,
                                            ProductUpdateRequestDto request) {
        Product product = getProductWithOwnerCheck(accountId, productId);

        product.update(
                request.getName(),
                request.getDescription() == null ? "" : request.getDescription(),
                BigDecimal.valueOf(request.getBasePrice()),
                request.getStockQuantity(),
                request.getProductType()
        );

        log.info("상품 수정: productId={}", productId);
        return toDto(product);
    }

    @Transactional
    public void deleteProduct(Long accountId, Long productId) {
        Product product = getProductWithOwnerCheck(accountId, productId);
        product.deactivate();
        log.info("상품 비활성화: productId={}", productId);
    }

    @Transactional
    public void updateProductStatus(Long accountId, Long productId, ProductStatusUpdateRequestDto request) {
        Product product = getProductWithOwnerCheck(accountId, productId);

        if (request.getStatus() == ProductStatus.SOLD_OUT) {
            product.soldOut();
        } else if (request.getStatus() == ProductStatus.ACTIVE) {
            product.activate();
        } else if (request.getStatus() == ProductStatus.INACTIVE) {
            product.inactive();
        } else {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    @Transactional
    public void updateStock(Long accountId, Long productId,
                            ProductUpdateStockRequestDto request) {
        Product product = getProductWithOwnerCheck(accountId, productId);
        product.updateStock(request.getStock());
        log.info("재고 수정: productId={}, stock={}", productId, request.getStock());
    }

    // ===================== 내부 유틸 =====================

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
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

    private ProductResponseDto toDto(Product product) {
        return ProductResponseDto.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .productType(product.getProductType().name())
                .status(product.getStatus().name())
                .viewCount(product.getViewCount())
                .createdAt(product.getCreatedAt())
                .build();
    }
}