package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.response.AdminStoreDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.application.store.mapper.StoreMapper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.store.entity.SettlementAccount;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.*;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStoreService {

    private final StoreRepository storeRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final StoreMapper storeMapper;

    @Transactional(readOnly = true)
    public Page<StoreListResponseDto> getStores(
            String keyword, String status, Pageable pageable) {
        return storeRepository.searchAdminStores(keyword, status, pageable)
                .map(storeMapper::toStoreListResponseDto);
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
                .businessHours(getBusinessHours(storeId))
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
                .settlementAccount(storeMapper.toSettlementDto(settlementAccount))
                .images(storeImageRepository.findByStore_StoreIdOrderByDisplayOrderAsc(storeId)
                        .stream().map(storeMapper::toImageDto).toList())
                .notices(storeNoticeRepository.findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(storeId)
                        .stream().map(storeMapper::toNoticeDto).toList())
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

    public List<StoreBusinessHourResponseDto> getBusinessHours(Long storeId) {
        return storeBusinessHourRepository.findByStore_StoreId(storeId)
                .stream()
                .sorted(Comparator.comparingInt(hour -> hour.getDayOfWeek().getOrder()))
                .map(storeMapper::toBusinessHourDto)
                .toList();
    }

    private String formatRegionName(Store store) {
        return String.join(" ",
                store.getRegion().getSiDo(),
                store.getRegion().getGunGu(),
                store.getRegion().getDong()
        );
    }
}