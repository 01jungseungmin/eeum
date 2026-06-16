package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.EventProductRequestDto;
import com.eeum.eeum.application.product.dto.response.EventProductResponseDto;
import com.eeum.eeum.application.product.mapper.ProductMapper;
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
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProductService {

    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final StoreRepository storeRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public List<EventProductResponseDto> getMyEventProducts(Long accountId) {
        Store store = getStore(accountId);

        return eventProductRepository
                .findByProduct_Store_StoreIdOrderByCreatedAtDesc(store.getStoreId())
                .stream()
                .map(productMapper::toEventProductResponseDto)
                .toList();
    }

    @Transactional
    public EventProductResponseDto createEventProduct(Long accountId, EventProductRequestDto request) {
        Store store = getStore(accountId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateProductOwner(product, store);
        validateEventTargetProduct(product);
        validateEventPeriodForCreate(request.getStartAt(), request.getEndAt());

        if (eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                product.getProductId(),
                EventProductStatus.ACTIVE,
                LocalDateTime.now()
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

        return productMapper.toEventProductResponseDto(eventProduct);
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

        validateEventPeriodForUpdate(request.getStartAt(), request.getEndAt());

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

        return productMapper.toEventProductResponseDto(eventProduct);
    }

    // 스케줄러 전용 — endAt이 지난 ACTIVE 이벤트 상품을 ENDED로 일괄 처리
    @Transactional
    public int endExpiredEventProducts() {
        return eventProductRepository.bulkEndExpiredEvents(
                EventProductStatus.ACTIVE, EventProductStatus.ENDED, LocalDateTime.now());
    }

    @Transactional
    public void deleteEventProduct(Long accountId, Long eventProductId) {
        EventProduct eventProduct = getEventProductWithOwnerCheck(accountId, eventProductId);
        eventProduct.delete();

        log.info("이벤트 상품 비활성화: eventProductId={}", eventProductId);
    }

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private EventProduct getEventProductWithOwnerCheck(Long accountId, Long eventProductId) {
        Store store = getStore(accountId);

        EventProduct eventProduct = eventProductRepository.findById(eventProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        validateProductOwner(eventProduct.getProduct(), store);

        return eventProduct;
    }

    private void validateProductOwner(Product product, Store store) {
        if (!product.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
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


    private void validateEventPeriodForCreate(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt) || startAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validateEventPeriodForUpdate(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt) || !endAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }
}