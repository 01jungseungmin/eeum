package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.EventProductRequestDto;
import com.eeum.eeum.application.product.dto.response.EventProductResponseDto;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.EventProductStatus;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProductService {

    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public List<EventProductResponseDto> getMyEventProducts(Long accountId) {
        Store store = getStore(accountId);

        return eventProductRepository
                .findByProduct_Store_StoreIdOrderByCreatedAtDesc(store.getStoreId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public EventProductResponseDto createEventProduct(Long accountId, EventProductRequestDto request) {
        Store store = getStore(accountId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateProductOwner(product, store);
        validateEventTargetProduct(product);
        validateEventPeriod(request.getStartAt(), request.getEndAt());

        if (eventProductRepository.existsByProduct_ProductIdAndStatus(
                product.getProductId(),
                EventProductStatus.ACTIVE
        )) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_ACTIVE);
        }

        BigDecimal eventPrice = BigDecimal.valueOf(request.getEventPrice());
        validateEventPrice(product, eventPrice);
        validateEventStock(product, request.getEventStock());

        EventProduct eventProduct = EventProduct.create(
                product,
                eventPrice,
                request.getEventStock(),
                request.getStartAt(),
                request.getEndAt()
        );

        eventProductRepository.save(eventProduct);

        log.info("이벤트 상품 등록: productId={}", product.getProductId());

        return toDto(eventProduct);
    }

    @Transactional
    public EventProductResponseDto updateEventProduct(
            Long accountId,
            Long eventProductId,
            EventProductRequestDto request
    ) {
        EventProduct eventProduct = getEventProductWithOwnerCheck(accountId, eventProductId);

        Product product = eventProduct.getProduct();

        if (!product.getProductId().equals(request.getProductId())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        validateEventPeriod(request.getStartAt(), request.getEndAt());

        BigDecimal eventPrice = BigDecimal.valueOf(request.getEventPrice());
        validateEventPrice(product, eventPrice);
        validateEventStock(product, request.getEventStock());

        eventProduct.update(
                eventPrice,
                request.getEventStock(),
                request.getStartAt(),
                request.getEndAt()
        );

        log.info("이벤트 상품 수정: eventProductId={}", eventProductId);

        return toDto(eventProduct);
    }

    @Transactional
    public void deleteEventProduct(Long accountId, Long eventProductId) {
        EventProduct eventProduct = getEventProductWithOwnerCheck(accountId, eventProductId);
        eventProduct.deactivate();

        log.info("이벤트 상품 비활성화: eventProductId={}", eventProductId);
    }

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private EventProduct getEventProductWithOwnerCheck(Long accountId, Long eventProductId) {
        Store store = getStore(accountId);

        EventProduct eventProduct = eventProductRepository.findById(eventProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_ALREADY_ACTIVE));

        validateProductOwner(eventProduct.getProduct(), store);

        return eventProduct;
    }

    private void validateProductOwner(Product product, Store store) {
        if (!product.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
    }

    private void validateEventTargetProduct(Product product) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (product.getProductType() == ProductType.MENU) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validateEventPrice(Product product, BigDecimal eventPrice) {
        if (eventPrice.compareTo(product.getPrice()) >= 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validateEventStock(Product product, Integer eventStock) {
        if (eventStock == null || eventStock <= 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (product.getStock() != null && eventStock > product.getStock()) {
            throw new BusinessException(ErrorCode.EVENT_OUT_OF_STOCK);
        }
    }

    private void validateEventPeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private EventProductResponseDto toDto(EventProduct eventProduct) {
        Product product = eventProduct.getProduct();

        return EventProductResponseDto.builder()
                .eventProductId(eventProduct.getEventProductId())
                .productId(product.getProductId())
                .productName(product.getName())
                .originalPrice(product.getPrice())
                .eventPrice(eventProduct.getEventPrice())
                .discountRate(calculateDiscountRate(product.getPrice(), eventProduct.getEventPrice()))
                .eventStock(eventProduct.getEventStock())
                .soldCount(eventProduct.getSoldCount())
                .remainingStock(eventProduct.getRemainingStock())
                .startAt(eventProduct.getStartAt())
                .endAt(eventProduct.getEndAt())
                .ongoing(eventProduct.isOngoing())
                .eventStatus(resolveEventStatus(eventProduct))
                .build();
    }

    private Integer calculateDiscountRate(BigDecimal originalPrice, BigDecimal eventPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        return originalPrice.subtract(eventPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String resolveEventStatus(EventProduct eventProduct) {
        LocalDateTime now = LocalDateTime.now();


        if (eventProduct.getRemainingStock() <= 0) {
            return "SOLD_OUT";
        }

        if (now.isBefore(eventProduct.getStartAt())) {
            return "SCHEDULED";
        }

        if (now.isAfter(eventProduct.getEndAt())) {
            return "ENDED";
        }

        return "ONGOING";
    }
}