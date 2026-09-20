package com.eeum.eeum.application.account.mapper;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.store.entity.Store;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OwnerApplicationMapper {

    // ===================== 사장 계정 응답 변환 =====================

    /**
     * 사장 본인이 보는 사업자 등록 정보.
     *
     * 상점은 아직 없을 수 있다 — 사장 가입 직후 상점 생성 전에도 이 화면은 열려야 하므로
     * store가 null이면 상점 항목만 비우고 나머지는 그대로 내려준다.
     * 사업자번호는 본인 화면이어도 마스킹을 유지한다(관리자용 변환과 다른 점).
     */
    public OwnerApplicationDetailResponseDto toOwnerApplicationResponseDto(OwnerInfo ownerInfo, Store store) {
        Account account = ownerInfo.getAccount();

        return OwnerApplicationDetailResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(account.getAccountId())
                .ownerName(account.getName())
                .businessNumber(MaskingUtil.maskBusinessNumber(ownerInfo.getBusinessNumber()))
                .openingDate(ownerInfo.getOpeningDate())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .reviewRequestedAt(ownerInfo.getReviewRequestedAt())
                .createdAt(ownerInfo.getCreatedAt())
                .storeId(store != null ? store.getStoreId() : null)
                .storeName(store != null ? store.getName() : null)
                .storeAddress(store != null ? store.getAddress() : null)
                .storePhone(store != null ? store.getPhone() : null)
                .storeCategoryId(store != null && store.getCategory() != null
                        ? store.getCategory().getCategoryId() : null)
                .storeCategoryName(store != null && store.getCategory() != null
                        ? store.getCategory().getName() : null)
                .storeDescription(store != null ? store.getDescription() : null)
                .storeStatus(store != null ? store.getStatus().name() : null)
                .build();
    }

    // ===================== 관리자의 사장 정보 응답 변환 =====================
    public OwnerApplicationDetailResponseDto toOwnerApplicationDetailResponseDto(OwnerInfo ownerInfo) {
        return OwnerApplicationDetailResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(ownerInfo.getAccount().getAccountId())
                .businessNumber(ownerInfo.getBusinessNumber())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .createdAt(ownerInfo.getCreatedAt())
                .build();
    }

    // ===================== 관리자의 사장 가게 정보 응답 변환 =====================
    public OwnerApplicationDetailResponseDto toOwnerAdminStoreResponseDto(OwnerInfo ownerInfo, Store store, List<StoreBusinessHourResponseDto> businessHours) {
        Account account = ownerInfo.getAccount();

        return OwnerApplicationDetailResponseDto.builder()
                .ownerInfoId(ownerInfo.getOwnerInfoId())
                .accountId(account.getAccountId())
                .ownerName(account.getName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .businessNumber(ownerInfo.getBusinessNumber())
                .openingDate(ownerInfo.getOpeningDate())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .reviewRequestedAt(ownerInfo.getReviewRequestedAt())
                .createdAt(ownerInfo.getCreatedAt())
                .storeId(store.getStoreId())
                .storeName(store.getName())
                .storeAddress(store.getAddress())
                .storePhone(store.getPhone())
                .storeCategoryId(store.getCategory() != null ? store.getCategory().getCategoryId() : null)
                .storeCategoryName(store.getCategory() != null ? store.getCategory().getName() : null)
                .storeDescription(store.getDescription())
                .businessHours(businessHours)
                .storeStatus(store.getStatus().name())
                .build();
    }

    // ===================== 승인 리스트 정보 응답 변환 =====================
    public OwnerChecklistResponseDto toOwnerChecklistResponseDto(
            OwnerInfo ownerInfo,
            boolean businessVerified,
            boolean storeInfoCompleted,
            boolean menuRegistered,
            boolean businessHoursSet,
            boolean settlementAccountRegistered
    ) {
        boolean allCompleted = businessVerified
                && storeInfoCompleted
                && menuRegistered
                && businessHoursSet
                && settlementAccountRegistered;

        return OwnerChecklistResponseDto.builder()
                .businessVerified(businessVerified)
                .storeInfoCompleted(storeInfoCompleted)
                .menuRegistered(menuRegistered)
                .businessHoursSet(businessHoursSet)
                .settlementAccountRegistered(settlementAccountRegistered)
                .allCompleted(allCompleted)
                .reviewRequestedAt(ownerInfo.getReviewRequestedAt())
                .approvalStatus(ownerInfo.getApprovalStatus().name())
                .rejectionReason(ownerInfo.getRejectionReason())
                .build();
    }
}
