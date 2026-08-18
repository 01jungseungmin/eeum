package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsedProductService {

    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRegionRepository accountRegionRepository;

    @Transactional
    public UsedProductDetailResponseDto create(Long sellerId, UsedProductCreateRequestDto request) {
        Account seller = accountRepository.findById(sellerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        Category category = getUsedCategoryOrThrow(request.getCategoryId());
        AccountRegion sellerRegion = getVerifiedRegionOrThrow(sellerId, request.getRegionId());

        UsedProduct product = UsedProduct.create(
                seller,
                category,
                sellerRegion.getRegion(),
                request.getTitle(),
                request.getContent(),
                request.getPriceType(),
                request.getPrice()
        );

        return UsedProductDetailResponseDto.from(usedProductRepository.save(product));
    }

    @Transactional(readOnly = true)
    public UsedProductDetailResponseDto getDetail(Long viewerId, Long usedProductId) {
        UsedProduct product = getActiveOrThrow(usedProductId);

        // 숨김 처리된 글은 작성자에게만 보인다. 남에게 403을 주면 "숨겨진 글이 있다"는 사실이 새어 나가므로
        // 존재하지 않는 것과 같은 응답을 준다.
        if (product.isHidden() && !product.isOwnedBy(viewerId)) {
            throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        }

        return UsedProductDetailResponseDto.from(product);
    }

    @Transactional
    public UsedProductDetailResponseDto update(
            Long sellerId,
            Long usedProductId,
            UsedProductUpdateRequestDto request
    ) {
        UsedProduct product = getOwnedOrThrow(sellerId, usedProductId);
        Category category = getUsedCategoryOrThrow(request.getCategoryId());

        product.updateInfo(
                category,
                request.getTitle(),
                request.getContent(),
                request.getPriceType(),
                request.getPrice()
        );

        return UsedProductDetailResponseDto.from(product);
    }

    @Transactional
    public void delete(Long sellerId, Long usedProductId) {
        UsedProduct product = getOwnedOrThrow(sellerId, usedProductId);

        // 예약 중이라는 건 상대가 거래를 기다리고 있다는 뜻이다. 말없이 사라지면
        // 상대는 이유를 알 수 없다. 예약을 먼저 취소하게 한다.
        if (product.getStatus() == UsedProductStatus.RESERVED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_DELETE_NOT_ALLOWED);
        }

        product.softDelete();
    }

    // ===================== 내부 헬퍼 =====================

    private UsedProduct getActiveOrThrow(Long usedProductId) {
        return usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(usedProductId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));
    }

    private UsedProduct getOwnedOrThrow(Long sellerId, Long usedProductId) {
        UsedProduct product = getActiveOrThrow(usedProductId);
        if (!product.isOwnedBy(sellerId)) {
            throw new ForbiddenException(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
        }
        return product;
    }

    // 비활성 카테고리와 가게용 카테고리를 모두 걸러낸다.
    private Category getUsedCategoryOrThrow(Long categoryId) {
        return categoryRepository
                .findByCategoryIdAndTypeAndIsActiveTrue(categoryId, CategoryType.USED)
                .orElseThrow(() -> new BusinessException(ErrorCode.USED_PRODUCT_INVALID_CATEGORY));
    }

    // 거래 희망 지역이 본인의 GPS 인증 완료 활동 지역인지 확인
    private AccountRegion getVerifiedRegionOrThrow(Long accountId, Long regionId) {
        AccountRegion accountRegion = accountRegionRepository
                .findByAccount_AccountIdAndRegion_RegionId(accountId, regionId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.REGION_ACCESS_REQUIRED));

        if (!accountRegion.isVerified()) {
            throw new ForbiddenException(ErrorCode.REGION_NOT_VERIFIED);
        }
        return accountRegion;
    }
}
