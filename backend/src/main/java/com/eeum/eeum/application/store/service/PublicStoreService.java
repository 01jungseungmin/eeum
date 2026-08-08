package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.product.dto.response.*;
import com.eeum.eeum.application.store.dto.request.NearbyStoreSearchCondition;
import com.eeum.eeum.application.store.dto.request.StoreSearchDto;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreNoticeResponseDto;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductImage;
import com.eeum.eeum.domain.product.entity.ProductOption;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.repository.*;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.entity.StoreImage;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicStoreService {

    private static final Duration PRODUCT_VIEW_TTL = Duration.ofHours(6);

    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final EventProductRepository eventProductRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionItemRepository productOptionItemRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final ChatRoomRepository chatRoomRepository;

    // ===================== 상점 목록 조회 =====================

    @Transactional(readOnly = true)
    public Page<StoreListResponseDto> getStores(
            Long categoryId,
            Long regionId,
            String keyword,
            Pageable pageable
    ) {
        StoreSearchDto condition = new StoreSearchDto();
        condition.setCategoryId(categoryId);
        condition.setRegionId(regionId);
        condition.setKeyword(keyword);

        return storeRepository.searchStores(condition, pageable)
                .map(this::toStoreListDto);
    }

    // ===================== 상점 상세 조회 =====================

    @Transactional(readOnly = true)
    public StoreDetailResponseDto getStoreDetail(Long storeId) {
        /*
         * 사용자 앱에서 상점 상세는 상태와 무관하게 보여주기로 함.
         * OPEN / TEMP_CLOSED / CLOSED 모두 조회 가능.
         * 단, 존재하지 않는 상점만 예외 처리.
         */
        Store store = getPublicVisibleStore(storeId);

        List<ImageResponseDto> images = storeImageRepository
                .findByStore_StoreIdOrderByDisplayOrderAsc(store.getStoreId())
                .stream()
                .map(this::toStoreImageDto)
                .toList();

        List<StoreNoticeResponseDto> notices = storeNoticeRepository
                .findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(store.getStoreId())
                .stream()
                .map(this::toNoticeDto)
                .toList();

        // 종료된 단톡방 ID를 내려주면 사용자가 폭파된 옛 방으로 접속을 시도하게 된다 —
        // 활성 방만, 여러 건이면 최신 방을 노출한다.
        Long chatRoomId = chatRoomRepository
                .findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                        ChatRoomRefType.STORE, storeId)
                .map(ChatRoom::getChatroomId)
                .orElse(null);

        return StoreDetailResponseDto.of(
                store,
                images,
                notices,
                getBusinessHourDtos(store.getStoreId()),
                chatRoomId
        );
    }

    // ===================== 상점 상품 목록 조회 =====================

    @Transactional(readOnly = true)
    public List<ProductListResponseDto> getStoreProducts(Long storeId) {
        getPublicVisibleStore(storeId);

        /*
         * 사용자 앱에서도 SOLD_OUT 상품은 보여준다.
         * 대신 구매 버튼은 프론트에서 비활성화한다.
         *
         * 제외 대상:
         * - INACTIVE 상품
         */
        return productRepository.findByStore_StoreIdAndStatusNot(storeId, ProductStatus.INACTIVE)
                .stream()
                .map(this::toProductListDto)
                .toList();
    }

    // ===================== 상품 상세 조회 =====================

    @Transactional
    public ProductDetailResponseDto getProductDetail(Long productId, String viewerKey) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        /*
         * 사용자 앱에서는 INACTIVE 상품 상세 조회를 막는다.
         * SOLD_OUT 상품은 조회 가능하다.
         */
        if (product.getStatus() == ProductStatus.INACTIVE
                || product.getStore().getAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        increaseViewCountIfFirstView(product, viewerKey);

        List<ImageResponseDto> images = productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toProductImageDto)
                .toList();

        Optional<EventProduct> activeEvent = eventProductRepository
                .findActiveEventByProductId(productId);

        return ProductDetailResponseDto.builder()
                .productId(product.getProductId())
                .storeId(product.getStore().getStoreId())
                .storeName(product.getStore().getName())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .productType(product.getProductType().name())
                .status(product.getStatus().name())
                .viewCount(product.getViewCount())
                .images(images)
                .eventPrice(activeEvent.map(EventProduct::getEventPrice).orElse(null))
                .hasEvent(activeEvent.isPresent())
                .eventProductId(activeEvent.map(EventProduct::getEventProductId).orElse(null))
                .remainingStock(activeEvent
                        .map(EventProduct::getRemainingStock)
                        .orElse(null))  // 추가 필요
                .build();
    }

    // ===================== 이벤트 상품 목록 조회 =====================

    @Transactional(readOnly = true)
    public List<EventProductListResponseDto> getEventProducts(Long storeId) {
        getPublicVisibleStore(storeId);

        return eventProductRepository.findActiveEventsByStoreId(storeId)
                .stream()
                .map(this::toEventListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreListResponseDto> getNearbyStores(
            double latitude,
            double longitude,
            double radiusKm,
            Long categoryId,
            Long regionId,
            String keyword
    ) {
        NearbyStoreSearchCondition condition = new NearbyStoreSearchCondition();
        condition.setLatitude(latitude);
        condition.setLongitude(longitude);
        condition.setRadiusKm(radiusKm);
        condition.setCategoryId(categoryId);
        condition.setRegionId(regionId);
        condition.setKeyword(keyword);

        return storeRepository.findNearbyStoresWithFilter(condition)
                .stream()
                .map(this::toStoreListDto)
                .toList();
    }

    // 상점 공지 목록 조회
    @Transactional(readOnly = true)
    public List<StoreNoticeResponseDto> getStoreNotices(Long storeId) {
        getPublicVisibleStore(storeId);

        return storeNoticeRepository
                .findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(storeId)
                .stream()
                .map(this::toNoticeDto)
                .toList();
    }

    // 상점 상품 카테고리 조회
    @Transactional(readOnly = true)
    public List<ProductCategoryResponseDto> getStoreProductCategories(Long storeId) {
        getPublicVisibleStore(storeId);

        return productCategoryRepository
                .findByStore_StoreIdAndIsActiveTrueOrderByDisplayOrderAsc(storeId)
                .stream()
                .map(c -> ProductCategoryResponseDto.builder()
                        .productCategoryId(c.getProductCategoryId())
                        .storeId(c.getStore().getStoreId())
                        .name(c.getName())
                        .displayOrder(c.getDisplayOrder())
                        .isActive(c.isActive())
                        .productCount(productCategoryRepository.countProductsByCategoryId(
                                c.getProductCategoryId()
                        ))
                        .build())
                .toList();
    }

    // 상품 옵션 조회
    @Transactional(readOnly = true)
    public List<ProductOptionDto> getProductOptions(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() == ProductStatus.INACTIVE
                || product.getStore().getAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return productOptionRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toOptionDto)
                .toList();
    }

    private ProductOptionDto toOptionDto(ProductOption option) {
        List<ProductOptionItemDto> items = productOptionItemRepository
                .findByProductOption_ProductOptionIdOrderByDisplayOrderAsc(
                        option.getProductOptionId())
                .stream()
                .map(item -> ProductOptionItemDto.builder()
                        .itemId(item.getProductOptionItemId())
                        .itemName(item.getItemName())
                        .additionalPrice(item.getAdditionalPrice())
                        .isDefault(item.isDefault())
                        .displayOrder(item.getDisplayOrder())
                        .isAvailable(item.isAvailable())
                        .build())
                .toList();

        return ProductOptionDto.builder()
                .optionId(option.getProductOptionId())
                .groupName(option.getGroupName())
                .selectionType(option.getSelectionType())
                .isRequired(option.isRequired())
                .displayOrder(option.getDisplayOrder())
                .items(items)
                .build();
    }

    // ===================== 내부 유틸 =====================

    private StoreListResponseDto toStoreListDto(Store store) {
        String thumbnailUrl = getStoreThumbnailUrl(store.getStoreId());

        return StoreListResponseDto.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .phone(store.getPhone())
                .description(store.getDescription())
                .status(store.getStatus().name())
                .rating(store.getRating())
                .favoriteCount(store.getFavoriteCount())
                .reviewCount(store.getReviewCount())
                .categoryId(store.getCategory() != null ? store.getCategory().getCategoryId() : null)
                .categoryName(store.getCategory() != null ? store.getCategory().getName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    private ProductListResponseDto toProductListDto(Product product) {
        String thumbnailUrl = getProductThumbnailUrl(product.getProductId());

        Optional<EventProduct> activeEvent = eventProductRepository
                .findActiveEventByProductId(product.getProductId());

        return ProductListResponseDto.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .productType(product.getProductType().name())
                .status(product.getStatus().name())
                .thumbnailUrl(thumbnailUrl)
                .eventPrice(activeEvent.map(EventProduct::getEventPrice).orElse(null))
                .hasEvent(activeEvent.isPresent())
                .productCategoryId(product.getProductCategory().getProductCategoryId())
                .build();
    }

    private EventProductListResponseDto toEventListDto(EventProduct eventProduct) {
        Product product = eventProduct.getProduct();

        String thumbnailUrl = getProductThumbnailUrl(product.getProductId());

        return EventProductListResponseDto.builder()
                .eventProductId(eventProduct.getEventProductId())
                .productId(product.getProductId())
                .productName(product.getName())
                .thumbnailUrl(thumbnailUrl)
                .originalPrice(product.getPrice())
                .eventPrice(eventProduct.getEventPrice())
                .eventStock(eventProduct.getEventStock())
                .soldCount(eventProduct.getSoldCount())
                .remainingStock(eventProduct.getRemainingStock())
                .startAt(eventProduct.getStartAt())
                .endAt(eventProduct.getEndAt())
                .ongoing(eventProduct.isOngoing())
                .build();
    }

    private ImageResponseDto toStoreImageDto(StoreImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getStoreImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .isThumbnail(image.isThumbnail())
                .build();
    }

    private ImageResponseDto toProductImageDto(ProductImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getProductImageId())
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
                .pinned(notice.isPinned())
                .createdAt(notice.getCreatedAt())
                .modifiedAt(notice.getModifiedAt())
                .build();
    }

    private String getStoreThumbnailUrl(Long storeId) {
        /*
         * TODO: 목록 조회 성능 개선 시 thumbnail 전용 쿼리 또는 projection 적용
         */
        return storeImageRepository
                .findByStore_StoreIdOrderByDisplayOrderAsc(storeId)
                .stream()
                .filter(StoreImage::isThumbnail)
                .findFirst()
                .map(StoreImage::getImageUrl)
                .orElse(null);
    }

    private String getProductThumbnailUrl(Long productId) {
        /*
         * TODO: 목록 조회 성능 개선 시 thumbnail 전용 쿼리 또는 projection 적용
         */
        return productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .filter(image -> image.isThumbnail())
                .findFirst()
                .map(image -> image.getImageUrl())
                .orElse(null);
    }

    private void increaseViewCountIfFirstView(Product product, String viewerKey) {
        /*
         * Redis 중복 조회 방지 정책
         *
         * viewerKey는 Controller에서 생성해서 넘긴다.
         * - 로그인 사용자: account:{accountId}
         * - 비회원: ip:{clientIp}
         *
         * 같은 viewerKey가 같은 productId를 6시간 내 다시 조회하면 조회수 증가 X
         */
        String redisKey = "view:product:" + product.getProductId() + ":" + viewerKey;

        Boolean firstView = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", PRODUCT_VIEW_TTL);

        if (Boolean.TRUE.equals(firstView)) {
            product.increaseViewCount();
        }
    }

    private Store getPublicVisibleStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        validatePublicVisibleStore(store);

        return store;
    }
    private void validatePublicVisibleStore(Store store) {
        if (store.getAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }

        if (store.getStatus() == StoreStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }

        boolean approved = ownerInfoRepository.existsByAccount_AccountIdAndApprovalStatus(
                store.getAccount().getAccountId(),
                ApprovalStatus.APPROVED
        );

        if (!approved) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }
    }

    private List<StoreBusinessHourResponseDto> getBusinessHourDtos(Long storeId) {
        return storeBusinessHourRepository.findByStore_StoreId(storeId)
                .stream()
                .sorted(Comparator.comparingInt(hour -> hour.getDayOfWeek().getOrder()))
                .map(this::toBusinessHourDto)
                .toList();
    }

    private StoreBusinessHourResponseDto toBusinessHourDto(StoreBusinessHour businessHour) {
        return StoreBusinessHourResponseDto.builder()
                .dayOfWeek(businessHour.getDayOfWeek())
                .dayLabel(businessHour.getDayOfWeek().getLabel())
                .closed(businessHour.isClosed())
                .openTime(businessHour.getOpenTime())
                .closeTime(businessHour.getCloseTime())
                .build();
    }
}