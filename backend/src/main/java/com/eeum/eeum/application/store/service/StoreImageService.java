package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.mapper.StoreMapper;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.common.dto.request.ImageUploadRequestDto;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreImage;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
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
public class StoreImageService {

    private static final int MAX_IMAGE_COUNT = 20;

    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreMapper storeMapper;

    @Transactional(readOnly = true)
    public List<ImageResponseDto> getImages(Long accountId) {
        Store store = getStore(accountId);
        return storeImageRepository
                .findByStore_StoreIdOrderByDisplayOrderAsc(store.getStoreId())
                .stream()
                .map(storeMapper::toImageResponseDto)
                .toList();
    }

    @Transactional
    public List<ImageResponseDto> addImages(Long accountId, ImageUploadListRequestDto request) {
        Store store = getStore(accountId);

        int currentCount = storeImageRepository.countByStore_StoreId(store.getStoreId());
        int requestCount = request.getImages().size();

        if (currentCount + requestCount > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        validateThumbnailCount(request);

        boolean hasExistingThumbnail = storeImageRepository
                .existsByStore_StoreIdAndIsThumbnailTrue(store.getStoreId());

        boolean hasNewThumbnail = request.getImages().stream()
                .anyMatch(ImageUploadRequestDto::isThumbnail);

        if (hasNewThumbnail) {
            storeImageRepository.findByStore_StoreIdOrderByDisplayOrderAsc(store.getStoreId())
                    .forEach(StoreImage::unmarkAsThumbnail);
        }

        List<StoreImage> images = new java.util.ArrayList<>();

        for (int i = 0; i < request.getImages().size(); i++) {
            ImageUploadRequestDto imageRequest = request.getImages().get(i);

            boolean isThumbnail = imageRequest.isThumbnail();

            if (!hasExistingThumbnail && !hasNewThumbnail && currentCount == 0 && i == 0) {
                isThumbnail = true;
            }

            int displayOrder = currentCount + i + 1;

            StoreImage image = StoreImage.create(
                    store,
                    imageRequest.getImageUrl(),
                    displayOrder,
                    isThumbnail
            );

            images.add(image);
        }

        List<StoreImage> savedImages = storeImageRepository.saveAll(images);

        log.info("상점 이미지 다중 등록: storeId={}, count={}",
                store.getStoreId(), savedImages.size());

        return savedImages.stream()
                .map(storeMapper::toImageResponseDto)
                .toList();
    }

    @Transactional
    public void deleteImage(Long accountId, Long imageId) {
        Store store = getStore(accountId);
        StoreImage image = getImageWithOwnerCheck(store, imageId);

        boolean wasThumbnail = image.isThumbnail();
        storeImageRepository.delete(image);

        // 대표 이미지 삭제 시 다음 이미지를 대표로 설정
        if (wasThumbnail) {
            storeImageRepository
                    .findByStore_StoreIdOrderByDisplayOrderAsc(store.getStoreId())
                    .stream()
                    .findFirst()
                    .ifPresent(StoreImage::markAsThumbnail);
        }

        log.info("상점 이미지 삭제: imageId={}", imageId);
    }

    @Transactional
    public void setThumbnail(Long accountId, Long imageId) {
        Store store = getStore(accountId);
        StoreImage newThumbnail = getImageWithOwnerCheck(store, imageId);

        // 기존 대표 이미지 해제
        storeImageRepository
                .findByStore_StoreIdOrderByDisplayOrderAsc(store.getStoreId())
                .forEach(StoreImage::unmarkAsThumbnail);

        newThumbnail.markAsThumbnail();
        log.info("상점 대표 이미지 변경: imageId={}", imageId);
    }

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreImage getImageWithOwnerCheck(Store store, Long imageId) {
        StoreImage image = storeImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        if (!image.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return image;
    }

    private void validateThumbnailCount(ImageUploadListRequestDto request) {
        long thumbnailCount = request.getImages().stream()
                .filter(image -> Boolean.TRUE.equals(image.isThumbnail()))
                .count();

        if (thumbnailCount > 1) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
    }
}