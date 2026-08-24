package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.response.OwnerApprovalStatusResponseDto;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.account.mapper.StoreApprovalMapper;
import com.eeum.eeum.application.product.dto.request.RepresentativeMenuCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessHourUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessInfoRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.event.OwnerApplicationSubmittedEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
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
import com.eeum.eeum.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerApprovalService {

    private final AccountRepository accountRepository;
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
        // 잠금 순서 account → owner_info. 관리자 승인/거절(AdminAccountService)과 같은 순서다.
        // 잠그지 않고 읽으면 PENDING을 본 뒤 관리자 승인이 ROLE_OWNER + APPROVED를 커밋하고,
        // 이 트랜잭션이 나중에 flush하며 상태를 PENDING으로 되돌린다. OwnerInfo에는 @Version이
        // 없어 dirty checking이 행 전체를 덮어쓰므로 ROLE_OWNER + PENDING이 그대로 남는다.
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);
        }

        // 잠금을 잡은 뒤 읽는다. getOwnerInfo()로 먼저 읽으면 그 인스턴스가 영속성 컨텍스트에
        // 남아 잠금 조회가 DB 최신 행 대신 1차 캐시를 돌려주고, 재조회의 의미가 사라진다.
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccountIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_OWNER_NOT_FOUND));

        if (ownerInfo.isApproved()) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_APPROVED);
        }

        // 재신청은 미접수·거절 상태에서만 허용한다. 이미 접수된 건을 다시 접수하면
        // 신청 시각이 갱신돼 대기열 순서가 밀리고, 관리자 알림이 요청 횟수만큼 재발행된다.
        // (거절 시 reviewRequestedAt은 남지만 REJECTED는 재신청 대상이므로 상태로 구분한다.)
        if (ownerInfo.isReviewRequested()
                && ownerInfo.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw new BusinessException(ErrorCode.OWNER_REVIEW_ALREADY_REQUESTED);
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