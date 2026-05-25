package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.request.*;
import com.eeum.eeum.application.store.dto.response.*;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
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
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final CategoryRepository categoryRepository;

    // ===================== 상점 조회/수정 =====================

    @Transactional(readOnly = true)
    public StoreResponseDto getMyStore(Long accountId) {
        Store store = getStore(accountId);
        return toDto(store);
    }

    @Transactional
    public StoreResponseDto updateStore(Long accountId, StoreUpdateRequestDto request) {
        Store store = getStore(accountId);

        Category category = categoryRepository
                .findByCategoryIdAndTypeAndIsActiveTrue(request.getCategoryId(), CategoryType.STORE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        store.updateCategory(category);
        store.updateBasicInfo(
                request.getName(),
                request.getAddress(),
                request.getPhone(),
                request.getDescription(),
                request.getBusinessHours()
        );

        log.info("상점 정보 수정: accountId={}", accountId);
        return toDto(store);
    }

    @Transactional
    public void updateStoreStatus(Long accountId, StoreStatusUpdateRequestDto request) {
        Store store = getStore(accountId);

        if (request.getStatus() == StoreStatus.OPEN) {
            store.reopen();
        } else if (request.getStatus() == StoreStatus.TEMP_CLOSED) {
            store.tempClose();
        } else {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        log.info("상점 상태 변경: accountId={}, status={}", accountId, request.getStatus());
    }

    // ===================== 공지 관리 =====================

    @Transactional(readOnly = true)
    public List<StoreNoticeResponseDto> getNotices(Long accountId) {
        Store store = getStore(accountId);
        return storeNoticeRepository
                .findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(store.getStoreId())
                .stream()
                .map(this::toNoticeDto)
                .toList();
    }

    @Transactional
    public StoreNoticeResponseDto createNotice(Long accountId, StoreNoticeRequestDto request) {
        Store store = getStore(accountId);
        StoreNotice notice = StoreNotice.create(
                store, request.getTitle(), request.getContent(), request.isPinned());
        storeNoticeRepository.save(notice);
        log.info("공지 등록: storeId={}", store.getStoreId());
        return toNoticeDto(notice);
    }

    @Transactional
    public StoreNoticeResponseDto updateNotice(Long accountId, Long noticeId,
            StoreNoticeRequestDto request) {
        StoreNotice notice = getNoticeWithOwnerCheck(accountId, noticeId);
        notice.update(request.getTitle(), request.getContent(), request.isPinned());
        return toNoticeDto(notice);
    }

    @Transactional
    public void deleteNotice(Long accountId, Long noticeId) {
        StoreNotice notice = getNoticeWithOwnerCheck(accountId, noticeId);
        notice.deactivate();
    }

    // ===================== 내부 유틸 =====================

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreNotice getNoticeWithOwnerCheck(Long accountId, Long noticeId) {
        Store store = getStore(accountId);
        StoreNotice notice = storeNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOTICE_NOT_FOUND));
        if (!notice.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return notice;
    }

    private StoreResponseDto toDto(Store store) {
        return StoreResponseDto.builder()
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
                .categoryId(store.getCategory() != null ? store.getCategory().getCategoryId() : null)
                .categoryName(store.getCategory() != null ? store.getCategory().getName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .createdAt(store.getCreatedAt())
                .build();
    }

    private StoreNoticeResponseDto toNoticeDto(StoreNotice n) {
        return StoreNoticeResponseDto.builder()
                .noticeId(n.getNoticeId())
                .title(n.getTitle())
                .content(n.getContent())
                .pinned(n.isPinned())
                .createdAt(n.getCreatedAt())
                .modifiedAt(n.getModifiedAt())
                .build();
    }
}