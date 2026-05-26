package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.product.dto.request.ProductCreateRequestDto;
import com.eeum.eeum.application.product.dto.request.RepresentativeMenuCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBasicInfoRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.product.entity.Product;
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

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerApprovalService {

    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;

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
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        validateReviewEditable(ownerInfo);

        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));

        store.updateBasicInfo(
                request.getName(),
                request.getAddress(),
                request.getPhone(),
                category,
                region,
                request.getLatitude(),
                request.getLongitude(),
                request.getDescription(),
                request.getBusinessHours()
        );
    }

    // ===================== 정산 계좌 등록/수정 =====================

    @Transactional
    public SettlementAccountResponseDto saveSettlementAccount(
            Long accountId,
            SettlementAccountRequestDto request
    ) {
        OwnerInfo ownerInfo = getOwnerInfo(accountId);

        validateReviewEditable(ownerInfo);

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

        if (ownerInfo.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }

        Store store = getStore(accountId);

        validateChecklistCompleted(ownerInfo, store);

        ownerInfo.requestReview();

        log.info("입점 심사 요청 완료: accountId={}", accountId);
    }

    // ===================== 대표 메뉴 설정 =====================

    @Transactional
    public void saveRepresentativeMenu(Long accountId, RepresentativeMenuCreateRequestDto request) {
        OwnerInfo ownerInfo = getOwnerInfo(accountId);

        validateReviewEditable(ownerInfo);

        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Product product = productRepository
                .findFirstByStore_StoreIdAndProductTypeOrderByCreatedAtAsc(
                        store.getStoreId(),
                        ProductType.MENU
                )
                .orElse(null);

        if (product == null) {
            Product newProduct = Product.create(
                    store,
                    request.getName(),
                    request.getDescription(),
                    BigDecimal.valueOf(request.getBasePrice()),
                    null,
                    ProductType.MENU
            );

            productRepository.save(newProduct);
            return;
        }

        product.update(
                request.getName(),
                request.getDescription(),
                BigDecimal.valueOf(request.getBasePrice()),
                null,
                ProductType.MENU
        );
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

    private void validateReviewEditable(OwnerInfo ownerInfo) {
        if (ownerInfo.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }
    }
}