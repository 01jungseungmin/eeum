package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.product.dto.request.RepresentativeMenuCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessHourUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessInfoRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.event.OwnerApplicationSubmittedEvent;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.SettlementAccount;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerApprovalService {

    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final OwnerApplicationMapper ownerApplicationMapper;
    private final StoreApprovalMapper storeApprovalMapper;
    private final ApplicationEventPublisher eventPublisher;

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

        boolean businessHoursSet =
                storeBusinessHourRepository.countByStore_StoreId(store.getStoreId()) == 7;

        boolean settlementAccountRegistered =
                settlementAccountRepository.existsByStore_StoreId(store.getStoreId());

        return ownerApplicationMapper.toOwnerChecklistResponseDto(
                ownerInfo,
                businessVerified,
                storeInfoCompleted,
                menuRegistered,
                businessHoursSet,
                settlementAccountRegistered
        );
    }

    // ===================== 영업시간 설정 =====================


    @Transactional
    public void updateBusinessHours(Long accountId,StoreBusinessHourUpdateRequestDto request) {
        OwnerInfo ownerInfo = getOwnerInfo(accountId);

        validateReviewEditable(ownerInfo);

        Store store = getStore(accountId);

        validateBusinessHoursRequest(request);

        Map<StoreDayOfWeek, StoreBusinessHour> existingMap =
                storeBusinessHourRepository.findByStore_StoreId(store.getStoreId())
                        .stream()
                        .collect(Collectors.toMap(
                                StoreBusinessHour::getDayOfWeek,
                                Function.identity()
                        ));

        for (StoreBusinessHourUpdateRequestDto.BusinessHourItem item : request.getBusinessHours()) {
            StoreBusinessHour businessHour = existingMap.get(item.getDayOfWeek());

            if (businessHour == null) {
                StoreBusinessHour newBusinessHour = StoreBusinessHour.create(
                        store,
                        item.getDayOfWeek(),
                        item.isClosed(),
                        item.getOpenTime(),
                        item.getCloseTime()
                );

                storeBusinessHourRepository.save(newBusinessHour);
                continue;
            }

            businessHour.update(
                    item.isClosed(),
                    item.getOpenTime(),
                    item.getCloseTime()
            );
        }

        log.info("상점 영업시간 저장 완료: accountId={}, storeId={}", accountId, store.getStoreId());
    }

    @Transactional
    public void updateStoreBusinessInfo(Long accountId, StoreBusinessInfoRequestDto request) {
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        validateReviewEditable(ownerInfo);

        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        store.updateBusinessInfo(
                category,
                request.getDescription()
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

        return storeApprovalMapper.toSettlementAccountDto(settlementAccount);
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

        eventPublisher.publishEvent(new OwnerApplicationSubmittedEvent(
                accountId,
                store.getAccount().getName(),
                store.getName()));
    }

    // ===================== 대표 메뉴 설정 =====================

    @Transactional
    public void saveRepresentativeMenu(Long accountId, RepresentativeMenuCreateRequestDto request) {
        OwnerInfo ownerInfo = getOwnerInfo(accountId);

        validateReviewEditable(ownerInfo);

        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        ProductCategory representativeCategory = getOrCreateRepresentativeCategory(store);

        Product product = productRepository
                .findFirstByStore_StoreIdAndProductTypeOrderByCreatedAtAsc(
                        store.getStoreId(),
                        ProductType.MENU
                )
                .orElse(null);

        if (product == null) {
            Product newProduct = Product.create(
                    store,
                    representativeCategory,
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
                representativeCategory,
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

        if (storeBusinessHourRepository.countByStore_StoreId(store.getStoreId()) != 7) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (store.getCategory() == null) {
            throw new BusinessException(ErrorCode.STORE_CATEGORY_REQUIRED);
        }

        if (!settlementAccountRepository.existsByStore_StoreId(store.getStoreId())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateReviewEditable(OwnerInfo ownerInfo) {
        if (ownerInfo.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }
    }

    private ProductCategory getOrCreateRepresentativeCategory(Store store) {
        return productCategoryRepository
                .findByStore_StoreIdAndName(store.getStoreId(), "대표 메뉴")
                .orElseGet(() -> productCategoryRepository.save(
                        ProductCategory.create(store, "대표 메뉴", 0)
                ));
    }

    private void validateBusinessHoursRequest(StoreBusinessHourUpdateRequestDto request) {
        if (request.getBusinessHours() == null || request.getBusinessHours().isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        long distinctDayCount = request.getBusinessHours().stream()
                .map(StoreBusinessHourUpdateRequestDto.BusinessHourItem::getDayOfWeek)
                .distinct()
                .count();

        if (distinctDayCount != request.getBusinessHours().size()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (request.getBusinessHours().size() != StoreDayOfWeek.values().length) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        for (StoreBusinessHourUpdateRequestDto.BusinessHourItem item : request.getBusinessHours()) {
            validateBusinessHourItem(item);
        }
    }

    private void validateBusinessHourItem(StoreBusinessHourUpdateRequestDto.BusinessHourItem item) {
        if (item.getDayOfWeek() == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (item.isClosed()) {
            return;
        }

        if (item.getOpenTime() == null || item.getCloseTime() == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (!item.getOpenTime().isBefore(item.getCloseTime())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }
}