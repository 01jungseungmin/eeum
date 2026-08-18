package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsedProductService {

    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final UsedProductImageService usedProductImageService;
    private final UsedProductImageRepository usedProductImageRepository;

    @Transactional
    public UsedProductDetailResponseDto create(Long sellerId, UsedProductCreateRequestDto request) {
        Account seller = accountRepository.findById(sellerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        Category category = getUsedCategoryOrThrow(request.getCategoryId());
        // 지역을 지정하지 않으면 선택한 동네에 올린다 — 앱에서 동네를 고른 뒤 글을 쓰는 흐름과 맞춘다.
        AccountRegion sellerRegion = request.getRegionId() == null
                ? getSelectedRegionOrThrow(sellerId)
                : getVerifiedRegionOrThrow(sellerId, request.getRegionId());

        UsedProduct product = UsedProduct.create(
                seller,
                category,
                sellerRegion.getRegion(),
                request.getTitle(),
                request.getContent(),
                request.getPriceType(),
                request.getPrice()
        );

        // 등록 직후에는 사진이 없다 — 사진은 별도 엔드포인트로 올린다.
        return UsedProductDetailResponseDto.from(usedProductRepository.save(product), List.of());
    }

    /**
     * 내 동네 중고 목록.
     *
     * <p>지역을 지정하지 않으면 내 인증 활동 지역 전체를 본다. 인증된 지역이 하나도 없으면
     * 빈 목록이 아니라 오류로 막는다 — 동네 인증이 이 서비스의 전제이고,
     * 빈 목록을 주면 사용자는 "매물이 없다"고 오해한다.
     */
    @Transactional(readOnly = true)
    public Slice<UsedProductSummaryResponseDto> getRegionProducts(
            Long viewerId,
            Long regionId,
            Pageable pageable
    ) {
        Long targetRegionId = resolveSelectedRegionId(viewerId, regionId);
        Slice<UsedProduct> products = usedProductRepository.findByRegion(targetRegionId, pageable);

        Map<Long, String> thumbnails = findThumbnailUrls(products.getContent());

        return products.map(product -> UsedProductSummaryResponseDto.of(
                product, thumbnails.get(product.getUsedProductId())));
    }

    @Transactional(readOnly = true)
    public UsedProductDetailResponseDto getDetail(Long viewerId, Long usedProductId) {
        UsedProduct product = getActiveOrThrow(usedProductId);

        // 숨김 처리된 글은 작성자에게만 보인다. 남에게 403을 주면 "숨겨진 글이 있다"는 사실이 새어 나가므로
        // 존재하지 않는 것과 같은 응답을 준다.
        if (product.isHidden() && !product.isOwnedBy(viewerId)) {
            throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        }

        return UsedProductDetailResponseDto.from(
                product, usedProductImageService.getImages(usedProductId));
    }

    // 상세 조회 + 조회수 증가.
    @Transactional
    public UsedProductDetailResponseDto getDetailAndCountView(Long viewerId, Long usedProductId) {
        UsedProductDetailResponseDto detail = getDetail(viewerId, usedProductId);

        if (!detail.getSellerId().equals(viewerId)) {
            usedProductRepository.increaseViewCount(usedProductId);
        }
        return detail;
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

        return UsedProductDetailResponseDto.from(
                product, usedProductImageService.getImages(usedProductId));
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

    // 조회 대상 지역을 정한다. regionId를 주면 그 지역이 내 인증 지역인지 확인하고,
    // 생략하면 내가 선택해 둔 동네(대표 지역)를 쓴다.
    // 활동 지역은 최대 2개지만 사용자는 그중 하나를 골라 쓰므로 둘을 합쳐 보여주지 않는다.
    private Long resolveSelectedRegionId(Long accountId, Long regionId) {
        if (regionId != null) {
            return getVerifiedRegionOrThrow(accountId, regionId).getRegion().getRegionId();
        }
        return getSelectedRegionOrThrow(accountId).getRegion().getRegionId();
    }

    // 사용자가 선택해 둔 동네(대표 지역)를 찾는다.
    // 주의: Account.primaryRegionId는 이름과 달리 regionId가 아니라 accountRegionId를 담는다.
    // 활동 지역 등록 행을 가리키는 값이라, 지역 자체와 혼동하면 엉뚱한 동네를 조회하게 된다.
    private AccountRegion getSelectedRegionOrThrow(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        Long selectedAccountRegionId = account.getPrimaryRegionId();
        if (selectedAccountRegionId == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        }

        AccountRegion selected = accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(selectedAccountRegionId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND));

        if (!selected.isVerified()) {
            throw new ForbiddenException(ErrorCode.REGION_NOT_VERIFIED);
        }
        return selected;
    }

    // 대표 사진을 한 번의 IN 쿼리로 모아 온다 — 게시글마다 조회하면 N+1이다.
    private Map<Long, String> findThumbnailUrls(List<UsedProduct> products) {
        if (products.isEmpty()) {
            return Map.of();
        }

        List<Long> productIds = products.stream().map(UsedProduct::getUsedProductId).toList();

        return usedProductImageRepository
                .findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(productIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getUsedProduct().getUsedProductId(),
                        UsedProductImage::getImageUrl,
                        (first, second) -> first));
    }

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
