package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.response.AdminStoreDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreNoticeResponseDto;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.store.entity.SettlementAccount;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreImage;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
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
    private final OwnerInfoRepository ownerInfoRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreNoticeRepository storeNoticeRepository;

    @Transactional(readOnly = true)
    public Page<StoreListResponseDto> getStores(
            String keyword, String status, Pageable pageable) {
        return storeRepository.searchAdminStores(keyword, status, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public AdminStoreDetailResponseDto getStoreDetail(Long storeId) {
        Store store = getStore(storeId);
        Account owner = store.getAccount();
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(owner.getAccountId()).orElse(null);
        SettlementAccount settlementAccount = settlementAccountRepository.findByStore_StoreId(storeId).orElse(null);

        return AdminStoreDetailResponseDto.builder()
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
                .regionId(store.getRegion() != null ? store.getRegion().getRegionId() : null)
                .regionName(store.getRegion() != null ? formatRegionName(store) : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .ownerAccountId(owner.getAccountId())
                .ownerName(owner.getName())
                .ownerEmail(owner.getEmail())
                .ownerPhone(owner.getPhone())
                .ownerRole(owner.getRole().name())
                .ownerStatus(owner.getStatus().name())
                .ownerInfoId(ownerInfo != null ? ownerInfo.getOwnerInfoId() : null)
                .businessNumber(ownerInfo != null ? ownerInfo.getBusinessNumber() : null)
                .openingDate(ownerInfo != null ? ownerInfo.getOpeningDate() : null)
                .approvalStatus(ownerInfo != null ? ownerInfo.getApprovalStatus().name() : null)
                .rejectionReason(ownerInfo != null ? ownerInfo.getRejectionReason() : null)
                .reviewRequestedAt(ownerInfo != null ? ownerInfo.getReviewRequestedAt() : null)
                .settlementAccount(toSettlementDto(settlementAccount))
                .images(storeImageRepository.findByStore_StoreIdOrderByDisplayOrderAsc(storeId)
                        .stream().map(this::toImageDto).toList())
                .notices(storeNoticeRepository.findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(storeId)
                        .stream().map(this::toNoticeDto).toList())
                .createdAt(store.getCreatedAt())
                .modifiedAt(store.getModifiedAt())
                .build();
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
    private SettlementAccountResponseDto toSettlementDto(SettlementAccount settlementAccount) {
        if (settlementAccount == null) {
            return null;
        }

        return SettlementAccountResponseDto.builder()
                .settlementAccountId(settlementAccount.getSettlementAccountId())
                .bankName(settlementAccount.getBankName())
                .accountNumber(MaskingUtil.maskAccountNumber(settlementAccount.getAccountNumber()))
                .accountHolder(settlementAccount.getAccountHolder())
                .build();
    }

    private ImageResponseDto toImageDto(StoreImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getStoreImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .isThumbnail(image.isThumbnail())
                .build();
    }

    private StoreNoticeResponseDto toNoticeDto(StoreNotice notice) {
        return StoreNoticeResponseDto.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .noticeType(notice.getNoticeType().name())
                .pinned(notice.isPinned())
                .createdAt(notice.getCreatedAt())
                .modifiedAt(notice.getModifiedAt())
                .build();
    }

    private String formatRegionName(Store store) {
        return String.join(" ",
                store.getRegion().getSiDo(),
                store.getRegion().getGunGu(),
                store.getRegion().getDong()
        );
    }
}