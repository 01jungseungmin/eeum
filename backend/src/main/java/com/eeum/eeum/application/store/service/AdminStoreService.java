package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStoreService {

    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public Page<StoreListResponseDto> getStores(
            String keyword, String status, Pageable pageable) {
        // TODO: QueryDSL로 keyword, status 필터 구현 필요
        return storeRepository.findAll(pageable)
                .map(this::toDto);
    }

    @Transactional
    public void suspendStore(Long adminId, Long storeId) {
        Store store = getStore(storeId);

        if (store.getStatus() == StoreStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        store.suspend();

        log.info("상점 관리자 정지: adminId={}, storeId={}", adminId, storeId);
    }

    @Transactional
    public void activateStore(Long adminId, Long storeId) {
        Store store = getStore(storeId);

        if (store.getStatus() != StoreStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        store.activate();

        log.info("상점 관리자 정지 해제: adminId={}, storeId={}", adminId, storeId);
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreListResponseDto toDto(Store store) {
        return StoreListResponseDto.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .phone(store.getPhone())
                .description(store.getDescription())
                .businessHours(store.getBusinessHours())
                .status(store.getStatus().name())
                .rating(store.getRating())
                .favoriteCount(store.getFavoriteCount())
                .reviewCount(store.getReviewCount())
                .categoryId(store.getCategory() != null
                        ? store.getCategory().getCategoryId() : null)
                .categoryName(store.getCategory() != null
                        ? store.getCategory().getName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .build();
    }
}