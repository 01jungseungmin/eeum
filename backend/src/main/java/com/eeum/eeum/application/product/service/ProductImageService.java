package com.eeum.eeum.application.product.service;

import com.eeum.eeum.common.dto.request.ImageUploadRequestDto;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductImage;
import com.eeum.eeum.domain.product.repository.ProductImageRepository;
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
public class ProductImageService {

    private static final int MAX_IMAGE_COUNT = 20;

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    @Transactional(readOnly = true)
    public List<ImageResponseDto> getImages(Long accountId, Long productId) {
        getProductWithOwnerCheck(accountId, productId);
        return productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ImageResponseDto addImage(Long accountId, Long productId,
            ImageUploadRequestDto request) {
        Product product = getProductWithOwnerCheck(accountId, productId);

        int currentCount = productImageRepository.countByProduct_ProductId(productId);
        if (currentCount >= MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        boolean isThumbnail = currentCount == 0;
        int displayOrder = currentCount + 1;

        ProductImage image = ProductImage.create(product, request.getImageUrl(),
                displayOrder, isThumbnail);
        productImageRepository.save(image);

        log.info("상품 이미지 등록: productId={}", productId);
        return toDto(image);
    }

    @Transactional
    public void deleteImage(Long accountId, Long productId, Long imageId) {
        getProductWithOwnerCheck(accountId, productId);
        ProductImage image = getImageWithProductCheck(productId, imageId);

        boolean wasThumbnail = image.isThumbnail();
        productImageRepository.delete(image);

        // 대표 이미지 삭제 시 다음 이미지를 대표로 설정
        if (wasThumbnail) {
            productImageRepository
                    .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                    .stream()
                    .findFirst()
                    .ifPresent(ProductImage::markAsThumbnail);
        }

        log.info("상품 이미지 삭제: imageId={}", imageId);
    }

    @Transactional
    public void setThumbnail(Long accountId, Long productId, Long imageId) {
        getProductWithOwnerCheck(accountId, productId);
        ProductImage newThumbnail = getImageWithProductCheck(productId, imageId);

        // 기존 대표 이미지 해제
        productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .forEach(ProductImage::unmarkAsThumbnail);

        newThumbnail.markAsThumbnail();
        log.info("상품 대표 이미지 변경: imageId={}", imageId);
    }

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

    private ProductImage getImageWithProductCheck(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        if (!image.getProduct().getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return image;
    }

    private ImageResponseDto toDto(ProductImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getProductImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .isThumbnail(image.isThumbnail())
                .build();
    }

    private void reorderProductImages(Long productId) {
        List<ProductImage> images = productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId);

        for (int i = 0; i < images.size(); i++) {
            images.get(i).changeDisplayOrder(i + 1);
        }
    }
}