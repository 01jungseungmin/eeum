package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBasicInfoRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.SettlementAccount;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerApprovalService {

    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ===================== 체크리스트 조회 =====================

    @Transactional(readOnly = true)
    public OwnerChecklistResponseDto getChecklist(Long accountId) {
        OwnerInfo ownerInfo = getOwnerInfo(accountId);
        Store store = getStore(accountId);

        boolean businessVerified = ownerInfo.isBusinessVerified();

        boolean storeInfoCompleted = hasText(store.getName())
                && hasText(store.getAddress())
                && hasText(store.getPhone())
                && store.getCategory() != null;

        boolean menuRegistered =
                productRepository.existsByStore_StoreIdAndProductType(
                        store.getStoreId(),
                        ProductType.MENU
                );

        boolean businessHoursSet = hasText(store.getBusinessHours());

        boolean settlementAccountRegistered =
                settlementAccountRepository.existsByStore_StoreId(store.getStoreId());

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

    // ===================== 영업시간 설정 =====================

    @Transactional
    public void updateStoreBasicInfo(Long accountId, StoreBasicInfoRequestDto request) {
        Store store = getStore(accountId);

        Category category = categoryRepository
                .findByCategoryIdAndTypeAndIsActiveTrue(
                        request.getCategoryId(),
                        CategoryType.STORE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        store.updateCategory(category);

        store.updateBasicInfo(
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                request.getDescription(),
                request.getBusinessHours()
        );

        log.info("상점 기본 정보 수정 완료: accountId={}", accountId);
    }

    // ===================== 정산 계좌 등록/수정 =====================

    @Transactional
    public SettlementAccountResponseDto saveSettlementAccount(
            Long accountId,
            SettlementAccountRequestDto request
    ) {
        Store store = getStore(accountId);

        SettlementAccount settlementAccount =
                settlementAccountRepository.findByStore_StoreId(store.getStoreId())
                        .orElse(null);

        if (settlementAccount == null) {
            settlementAccount = SettlementAccount.create(
                    store,
                    request.getBankName(),
                    request.getAccountNumber(),
                    request.getAccountHolder()
            );
            settlementAccountRepository.save(settlementAccount);
        } else {
            settlementAccount.update(
                    request.getBankName(),
                    request.getAccountNumber(),
                    request.getAccountHolder()
            );
        }

        log.info("정산 계좌 저장 완료: accountId={}", accountId);

        return toSettlementAccountDto(settlementAccount);
    }

    // ===================== 심사 요청 =====================

    @Transactional
    public void requestReview(Long accountId) {
        OwnerInfo ownerInfo = getOwnerInfo(accountId);
        Store store = getStore(accountId);

        validateChecklistCompleted(ownerInfo, store);

        ownerInfo.requestReview();

        log.info("입점 심사 요청 완료: accountId={}", accountId);
    }

    // ===================== 내부 유틸 =====================

    private OwnerInfo getOwnerInfo(Long accountId) {
        return ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));
    }

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private void validateChecklistCompleted(OwnerInfo ownerInfo, Store store) {
        if (!ownerInfo.isBusinessVerified()) {
            throw new BusinessException(ErrorCode.BUSINESS_VERIFY_FAILED);
        }

        if (!hasText(store.getName()) || !hasText(store.getAddress()) || !hasText(store.getPhone())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        boolean menuRegistered =
                productRepository.existsByStore_StoreIdAndProductType(
                        store.getStoreId(),
                        ProductType.MENU
                );

        if (!menuRegistered) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (!hasText(store.getBusinessHours())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (store.getCategory() == null) {
            throw new BusinessException(ErrorCode.STORE_CATEGORY_REQUIRED);
        }

        if (!settlementAccountRepository.existsByStore_StoreId(store.getStoreId())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private SettlementAccountResponseDto toSettlementAccountDto(SettlementAccount settlementAccount) {
        return SettlementAccountResponseDto.builder()
                .settlementAccountId(settlementAccount.getSettlementAccountId())
                .bankName(settlementAccount.getBankName())
                .accountNumber(maskAccountNumber(settlementAccount.getAccountNumber()))
                .accountHolder(settlementAccount.getAccountHolder())
                .build();
    }

    private String maskAccountNumber(String accountNumber) {
        if (!hasText(accountNumber) || accountNumber.length() < 4) {
            return accountNumber;
        }

        return accountNumber.substring(0, 3)
                + "-****-"
                + accountNumber.substring(accountNumber.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}