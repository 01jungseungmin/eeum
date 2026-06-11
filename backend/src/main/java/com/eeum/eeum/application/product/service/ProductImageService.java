package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.mapper.ProductMapper;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
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
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public ImageResponseDto getImage(Long accountId, Long productId) {
        getProductWithOwnerCheck(accountId, productId);

        ProductImage productImage = productImageRepository
                .findFirstByProduct_ProductIdAndIsThumbnailTrueOrderByDisplayOrderAsc(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND));

        return productMapper.toImageResponseDto(productImage);
    }

    @Transactional(readOnly = true)
    public List<ImageResponseDto> getImages(Long accountId, Long productId) {
        getProductWithOwnerCheck(accountId, productId);
        return productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(productMapper::toImageResponseDto)
                .toList();
    }

    @Transactional
    public List<ImageResponseDto> addImages(
            Long accountId,
            Long productId,
            ImageUploadListRequestDto request
    ) {
        Product product = getProductWithOwnerCheck(accountId, productId);

        int currentCount = productImageRepository.countByProduct_ProductId(productId);
        int requestCount = request.getImages().size();

        if (currentCount + requestCount > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        validateThumbnailCount(request);

        boolean hasExistingThumbnail = productImageRepository
                .existsByProduct_ProductIdAndIsThumbnailTrue(productId);

        boolean hasNewThumbnail = request.getImages().stream()
                .anyMatch(ImageUploadRequestDto::isThumbnail);

        if (hasNewThumbnail) {
            productImageRepository.findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                    .forEach(ProductImage::unmarkAsThumbnail);
        }

        List<ProductImage> images = new java.util.ArrayList<>();

        for (int i = 0; i < request.getImages().size(); i++) {
            ImageUploadRequestDto imageRequest = request.getImages().get(i);

            boolean isThumbnail = imageRequest.isThumbnail();

            if (!hasExistingThumbnail && !hasNewThumbnail && currentCount == 0 && i == 0) {
                isThumbnail = true;
            }

            int displayOrder = currentCount + i + 1;

            ProductImage image = ProductImage.create(
                    product,
                    imageRequest.getImageUrl(),
                    displayOrder,
                    isThumbnail
            );

            images.add(image);
        }

        List<ProductImage> savedImages = productImageRepository.saveAll(images);

        log.info("상품 이미지 다중 등록: productId={}, count={}",
                productId, savedImages.size());

        return savedImages.stream()
                .map(productMapper::toImageResponseDto)
                .toList();
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

    private void validateThumbnailCount(ImageUploadListRequestDto request) {
        long thumbnailCount = request.getImages().stream()
                .filter(ImageUploadRequestDto::isThumbnail)
                .count();

        if (thumbnailCount > 1) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }
    }
}