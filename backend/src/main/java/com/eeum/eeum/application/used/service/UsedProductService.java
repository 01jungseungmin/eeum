package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductSearchRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductImageResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import java.util.ArrayDeque;
import java.util.Deque;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.event.UsedProductSoldEvent;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.domain.used.repository.UsedProductCursor;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.used.repository.UsedProductSearchCondition;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsedProductService {

    private final UsedProductRepository usedProductRepository;
    private final EntityManager entityManager;
    private final AccountRepository accountRepository;
    private final AccountWriteGuard accountWriteGuard;
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CategoryRepository categoryRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final RegionRepository regionRepository;
    private final UsedProductImageService usedProductImageService;
    private final UsedProductImageRepository usedProductImageRepository;
    private final FavoriteService favoriteService;

    @Transactional
    public UsedProductDetailResponseDto create(Long sellerId, UsedProductCreateRequestDto request) {
        // 잠금 순서는 account → product다. 규약은 AccountWriteGuard 참고.
        Account seller = accountWriteGuard.lockActive(sellerId);

        Category category = getUsedCategoryOrThrow(request.getCategoryId());
        // 등록은 조회와 달리 GPS 인증된 지역을 요구한다 — 아무 동네에나 매물을 뿌리는 것을 막는다.
        // 지역을 지정하지 않으면 선택한 동네에 올린다 (앱에서 동네를 고른 뒤 글을 쓰는 흐름과 맞춤).
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
        return UsedProductDetailResponseDto.from(
                usedProductRepository.save(product), List.of(), sellerId);
    }

    /**
     * 내 동네 중고 목록 — 커서 무한 스크롤.
     *
     * <p>페이지 번호를 쓰지 않는 이유는 새 글이 목록 맨 앞에 꽂혀, 스크롤 도중 등록된 한 건에
     * 경계 항목이 중복되거나 누락되기 때문이다. {@code pageable}에서는 정렬과 크기만 쓴다.
     */
    @Transactional(readOnly = true)
    public CursorSlice<UsedProductSummaryResponseDto> getRegionProducts(
            Long viewerId,
            UsedProductSearchRequestDto request,
            String cursorValue,
            Long cursorId,
            Pageable pageable
    ) {
        // 커서 조립은 여기서 한다 — 컨트롤러가 리포지토리 패키지를 참조하지 않도록(LayerRuleTest).
        UsedProductCursor cursor = UsedProductCursor.ofNullable(cursorValue, cursorId);
        validatePriceRange(request.getMinPrice(), request.getMaxPrice());

        Long targetRegionId = resolveViewRegionId(viewerId, request.getRegionId());

        // 지역만 서버가 정하고 나머지 필터는 요청한 그대로 넘긴다.
        UsedProductSearchCondition resolved = new UsedProductSearchCondition(
                targetRegionId,
                request.getKeyword(),
                resolveCategoryIds(request.getCategoryId()),
                request.getPriceType(),
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getStatuses()
        );

        CursorSlice<UsedProduct> products = usedProductRepository.search(
                resolved, cursor, pageable.getPageSize(), pageable.getSort());

        Map<Long, String> thumbnails = findThumbnailUrls(products.getContent());

        return products.map(product -> UsedProductSummaryResponseDto.of(
                product, thumbnails.get(product.getUsedProductId())));
    }

    @Transactional(readOnly = true)
    public UsedProductDetailResponseDto getDetail(Long viewerId, Long usedProductId) {
        UsedProduct product = getVisibleOrThrow(viewerId, usedProductId);

        return UsedProductDetailResponseDto.from(
                product, usedProductImageService.getImages(usedProductId), viewerId);
    }

    // 상세 조회 + 조회수 증가.
    @Transactional
    public UsedProductDetailResponseDto getDetailAndCountView(Long viewerId, Long usedProductId) {
        UsedProduct product = getVisibleOrThrow(viewerId, usedProductId);
        List<UsedProductImageResponseDto> images = usedProductImageService.getImages(usedProductId);

        // 비회원 조회도 센다. 판매자 본인 조회만 제외한다.
        if (!product.isOwnedBy(viewerId)) {
            // 갱신 행이 0이면 공개 확인 이후 숨김·삭제가 커밋된 것이다.
            // 그 상태의 글을 응답으로 내보내면 상세 조회의 노출 정책이 무의미해진다.
            if (usedProductRepository.increaseViewCount(usedProductId) == 0) {
                throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
            }
            // 조회수는 QueryDSL bulk UPDATE라 영속성 컨텍스트를 거치지 않는다.
            // 다시 조회해도 1차 캐시의 기존 인스턴스가 그대로 나오므로 refresh로 DB 값을 다시 읽는다.
            // 이걸 빼면 방금 센 이번 조회가 빠진 값이 응답에 담겨 항상 실제보다 1 작다.
            entityManager.refresh(product);
        }

        return UsedProductDetailResponseDto.from(product, images, viewerId);
    }

    // 최소 가격이 최대 가격보다 크면 결과가 반드시 빈다.
    // 빈 목록으로 응답하면 클라이언트는 "그 조건에 매물이 없다"로 읽어 잘못된 조건을 계속 보낸다.
    // 두 값을 함께 봐야 하는 검증이라 파라미터 애노테이션으로는 표현할 수 없다.
    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException(ErrorCode.USED_PRODUCT_INVALID_PRICE_RANGE);
        }
    }

    // 숨김 처리된 글은 작성자에게만 보인다. 남에게 403을 주면 "숨겨진 글이 있다"는 사실이 새어 나가므로
    // 존재하지 않는 것과 같은 응답을 준다.
    // 판매자가 탈퇴한 글은 작성자에게도 보이지 않는다 — 탈퇴 계정은 로그인 자체가 막힌다.
    private UsedProduct getVisibleOrThrow(Long viewerId, Long usedProductId) {
        UsedProduct product = getActiveOrThrow(usedProductId);

        if (!product.getSeller().isActive()) {
            throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        }

        if (product.isHidden() && !product.isOwnedBy(viewerId)) {
            throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        }
        return product;
    }

    @Transactional
    public UsedProductDetailResponseDto update(
            Long sellerId,
            Long usedProductId,
            UsedProductUpdateRequestDto request
    ) {
        accountWriteGuard.lockActive(sellerId);
        // 관리자 숨김·삭제와 같은 행을 다투므로 같은 비관적 잠금을 쓴다.
        // 잠그지 않으면 수정과 조치가 서로의 변경을 덮어쓴다.
        UsedProduct product = getOwnedForUpdateOrThrow(sellerId, usedProductId);
        Category category = getUsedCategoryOrThrow(request.getCategoryId());

        product.updateInfo(
                category,
                request.getTitle(),
                request.getContent(),
                request.getPriceType(),
                request.getPrice()
        );

        // modifiedAt은 flush 시점에 채워진다. 먼저 반영하지 않으면 응답에 수정 전 값이 담긴다.
        usedProductRepository.flush();

        return UsedProductDetailResponseDto.from(
                product, usedProductImageService.getImages(usedProductId), sellerId);
    }

    // ===================== 거래 상태 =====================

    /**
     * 예약 처리. 구매자 지정은 선택이다 — 상대 없이 "예약중"만 표시하는 흐름을 막지 않는다.
     *
     * <p>잠금 순서는 수정·삭제와 같은 account → used_product다. 관리자 숨김·삭제 조치와
     * 같은 행을 다투므로, 잠그지 않으면 사라진 글이 예약 상태로 되살아난다.
     */
    @Transactional
    public UsedProductDetailResponseDto reserve(Long sellerId, Long usedProductId, Long buyerId) {
        return changeTradeStatus(sellerId, usedProductId, buyerId,
                (product, buyer) -> product.reserve(buyer));
    }

    @Transactional
    public UsedProductDetailResponseDto cancelReservation(Long sellerId, Long usedProductId) {
        return changeTradeStatus(sellerId, usedProductId, null,
                (product, buyer) -> product.cancelReservation());
    }

    /**
     * 판매완료 처리. 여기서 확정된 구매자가 후기 작성 자격의 근거가 된다.
     *
     * <p>구매자를 생략하면 예약 때 지정해 둔 상대를 그대로 유지한다.
     * 앱 밖에서 성사된 거래는 구매자 없이 완료할 수 있고, 그 거래에는 후기가 붙지 않는다.
     *
     * <p><b>생략한 경우에도 그 상대를 검증한다.</b> 예약 이후 탈퇴·정지했을 수 있는데,
     * 그대로 확정하면 비활성 계정이 거래 구매자이자 후기 자격자로 남고 판매완료 알림까지 간다.
     * 검증 대상을 맞추기 위해 예약 상대를 미리 읽어 명시적으로 넘긴다.
     */
    @Transactional
    public UsedProductDetailResponseDto markSold(Long sellerId, Long usedProductId, Long buyerId) {
        // 잠금 없는 사전 읽기. 낡은 값은 상품을 잠근 뒤 아래에서 대조해 걸러낸다.
        Long effectiveBuyerId = buyerId != null
                ? buyerId
                : usedProductRepository.findBuyerIdByUsedProductId(usedProductId).orElse(null);

        return changeTradeStatus(sellerId, usedProductId, effectiveBuyerId,
                (product, buyer) -> {
                    if (buyerId == null) {
                        assertReservedBuyerUnchanged(product, effectiveBuyerId);
                    }

                    product.markSold(buyer);

                    // 후기를 쓸 상대가 있을 때만 알린다.
                    Account confirmed = product.getBuyer();
                    if (confirmed != null) {
                        eventPublisher.publishEvent(new UsedProductSoldEvent(
                                usedProductId, confirmed.getAccountId(), product.getTitle()));
                    }
                });
    }

    /**
     * 사전 읽기 이후 예약 상대가 바뀌지 않았는지 확인한다.
     *
     * <p>잠금 순서(account → used_product) 때문에 구매자 ID를 상품보다 먼저 읽어야 하는데,
     * 그 사이 다른 상태 전이가 예약 상대를 바꿀 수 있다. 그대로 진행하면
     * <b>잠그지도 검증하지도 않은 계정</b>이 구매자로 확정된다. 막고 재시도하게 한다.
     */
    private void assertReservedBuyerUnchanged(UsedProduct product, Long expectedBuyerId) {
        Long currentBuyerId = product.getBuyer() == null
                ? null
                : product.getBuyer().getAccountId();
        if (!Objects.equals(currentBuyerId, expectedBuyerId)) {
            throw new ConflictException(ErrorCode.USED_PRODUCT_BUYER_CHANGED);
        }
    }

    // 상태 전이 3종의 공통 골격 — 잠금·소유권·구매자 조회가 같고 전이 규칙만 다르다.
    // 전이 검증 자체는 엔티티가 한다(어느 경로로 불러도 같은 규칙이 적용되도록).
    private UsedProductDetailResponseDto changeTradeStatus(
            Long sellerId,
            Long usedProductId,
            Long buyerId,
            BiConsumer<UsedProduct, Account> transition
    ) {
        // account → used_product 순서. 두 계정은 ID 오름차순으로 잠가
        // 반대 방향 요청과 교착되지 않게 한다(문의방 생성과 같은 규약).
        Account buyer = null;
        if (buyerId == null) {
            accountWriteGuard.lockActive(sellerId);
        } else if (sellerId <= buyerId) {
            accountWriteGuard.lockActive(sellerId);
            buyer = lockAssignableBuyerOrThrow(buyerId);
        } else {
            buyer = lockAssignableBuyerOrThrow(buyerId);
            accountWriteGuard.lockActive(sellerId);
        }

        UsedProduct product = getOwnedForUpdateOrThrow(sellerId, usedProductId);

        if (buyer != null) {
            assertInquiredThisProduct(usedProductId, buyerId);
        }

        transition.accept(product, buyer);

        // 상태·구매자 변경을 먼저 반영해야 응답이 변경 전 값을 담지 않는다.
        usedProductRepository.flush();
        return UsedProductDetailResponseDto.from(
                product, usedProductImageService.getImages(usedProductId), sellerId);
    }

    @Transactional
    public void delete(Long sellerId, Long usedProductId) {
        // 찜 정리까지 하는 경로라 신고 조치와 같은 비관적 잠금을 쓴다.
        // 잠그지 않으면 삭제 직후 들어온 찜이 정리를 지나쳐 죽은 찜으로 남는다.
        accountWriteGuard.lockActive(sellerId);
        UsedProduct product = getOwnedForUpdateOrThrow(sellerId, usedProductId);

        // 예약 중이라는 건 상대가 거래를 기다리고 있다는 뜻이다. 말없이 사라지면
        // 상대는 이유를 알 수 없다. 예약을 먼저 취소하게 한다.
        if (product.getStatus() == UsedProductStatus.RESERVED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_DELETE_NOT_ALLOWED);
        }

        product.softDelete();

        // 찜을 함께 정리한다. 남겨두면 다른 사용자의 찜 목록에 사라진 글이 계속 남는다.
        // 같은 트랜잭션에서 처리해야 잠깐이라도 죽은 찜이 보이는 구간이 생기지 않는다.
        favoriteService.deleteAllByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, usedProductId);

        // 이 게시글을 가리키는 알림은 지우지 않는다. 후기 자격이 거래 사실 기준이라
        // 후기 요청 알림은 글이 사라진 뒤에도 구매자가 후기를 쓰는 유일한 진입점이다.
        // (NotificationService.deleteAllByRefTypeAndRefId를 여기에 배선하면 그 경로가 끊긴다.)
    }

    /**
     * 거래 상대로 지정할 수 있는 계정인지 확인한다.
     *
     * <p>존재만 보면 탈퇴·정지·익명화된 계정도 구매자로 확정되고, 그 계정으로 후기 요청 알림과
     * 푸시가 발송된다. 다른 쓰기 경로가 모두 actor에게 {@code AccountWriteGuard}를 적용하는데
     * 이 참조만 예외였다.
     *
     * <p>구매자 행을 잠그지는 않는다. 여기서 바꾸는 값이 아니고, 판매자 계정에 이어 두 번째
     * 계정 행을 잠그면 서로를 구매자로 지정하는 두 거래가 순환 대기할 수 있다.
     */
    /**
     * 구매자로 지정할 계정을 잠그고 사용 가능 상태를 확인한다.
     *
     * <p>잠그는 이유는 상태를 판정하기 때문이다. 잠그지 않으면 확인 직후 탈퇴가 커밋돼
     * 탈퇴한 계정이 구매자로 확정될 수 있다(문의방 생성의 판매자 잠금과 같은 이유).
     */
    private Account lockAssignableBuyerOrThrow(Long buyerId) {
        Account buyer = accountRepository.findByIdWithLock(buyerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!buyer.isActive()) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_BUYER);
        }
        return buyer;
    }

    /**
     * 이 상품으로 문의한 적이 있는 상대만 구매자로 지정할 수 있다.
     *
     * <p>존재하는 계정이기만 하면 지정할 수 있으면, 판매자가 아무 계정이나 구매자로 세워
     * 후기 작성 권한과 판매완료 알림을 줄 수 있다. 지목당한 사람은 하지도 않은 거래의
     * 후기 요청을 받는다.
     *
     * <p>실패 사유를 계정 상태와 구분하지 않는다 — 구분하면 판매자가 임의의 계정 ID로
     * 다른 사용자의 상태를 떠볼 수 있다.
     */
    private void assertInquiredThisProduct(Long usedProductId, Long buyerId) {
        boolean inquired = chatRoomRepository.existsByRefTypeAndRefIdAndBuyerAccountId(
                ChatRoomRefType.USED_PRODUCT, usedProductId, buyerId);
        if (!inquired) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_BUYER);
        }
    }

    // ===================== 내부 헬퍼 =====================

    // 조회 대상 지역을 정한다. regionId를 주면 그 지역이 내 인증 지역인지 확인하고,
    // 생략하면 내가 선택해 둔 동네(대표 지역)를 쓴다.
    // 활동 지역은 최대 2개지만 사용자는 그중 하나를 골라 쓰므로 둘을 합쳐 보여주지 않는다.
    private Long resolveViewRegionId(Long accountId, Long regionId) {
        // 지역을 지정하면 그대로 본다. 내 활동 지역이 아니어도 막지 않는다 —
        // 둘러보기는 열어두고, 실제 거래(채팅)에서 지역 인증을 요구한다.
        if (regionId != null) {
            if (!regionRepository.existsById(regionId)) {
                throw new NotFoundException(ErrorCode.REGION_NOT_FOUND);
            }
            return regionId;
        }

        // 지정하지 않았으면 내가 선택해 둔 동네를 쓴다. 비회원이거나 선택한 동네가 없으면
        // 어느 동네를 보여줄지 알 수 없으므로 지역을 지정하라고 알린다.
        Long selectedRegionId = findSelectedRegionId(accountId);
        if (selectedRegionId == null) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_REGION_REQUIRED);
        }
        return selectedRegionId;
    }

    // 조회용 — 선택한 동네가 없거나 비회원이면 null. GPS 인증 여부는 보지 않는다.
    private Long findSelectedRegionId(Long accountId) {
        if (accountId == null) {
            return null;
        }

        return accountRepository.findById(accountId)
                .map(Account::getPrimaryRegionId)
                .flatMap(selectedAccountRegionId -> accountRegionRepository
                        .findByAccountRegionIdAndAccount_AccountId(selectedAccountRegionId, accountId))
                .map(accountRegion -> accountRegion.getRegion().getRegionId())
                .orElse(null);
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

    // 선택한 카테고리와 그 하위 전체를 펼친다.
    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        List<Category> categories = categoryRepository
                .findAllByTypeOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(CategoryType.USED);

        boolean exists = categories.stream()
                .anyMatch(category -> category.getCategoryId().equals(categoryId));
        if (!exists) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_CATEGORY);
        }

        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);

            categories.stream()
                    .filter(category -> current.equals(category.getParentId()))
                    .map(Category::getCategoryId)
                    .forEach(queue::add);
        }
        return result;
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
        assertOwned(product, sellerId);
        return product;
    }

    // 잠금 조회 버전 — 찜·신고 조치와 경쟁하는 쓰기 경로에서 사용한다.
    // 잠금 조회에는 삭제 필터가 없으므로 여기서 거른다.
    private UsedProduct getOwnedForUpdateOrThrow(Long sellerId, Long usedProductId) {
        UsedProduct product = usedProductRepository.findByUsedProductIdForUpdate(usedProductId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));
        assertOwned(product, sellerId);
        return product;
    }

    private void assertOwned(UsedProduct product, Long sellerId) {
        // 비공개 글(숨김·판매자 탈퇴)은 비소유자에게 없는 것으로 응답한다.
        // 상세 조회는 404인데 수정·삭제만 403이면 그 차이로 존재가 드러난다.
        // 숨김만 막고 판매자 탈퇴를 빠뜨리면 그쪽으로 같은 구멍이 남는다.
        // 공개 글의 403은 유지한다 — 존재가 이미 공개라 404로 바꾸면 정상적인 권한 오류를 가린다.
        if (!product.isPubliclyVisible() && !product.isOwnedBy(sellerId)) {
            throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        }

        if (!product.isOwnedBy(sellerId)) {
            throw new ForbiddenException(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
        }
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
