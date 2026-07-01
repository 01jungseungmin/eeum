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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventProductServiceTest {

    @InjectMocks
    private EventProductService eventProductService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EventProductRepository eventProductRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ProductMapper productMapper;

    // ──────────────────── Helpers ────────────────────

    private Store createStore(Long storeId) {
        Store store = Store.createForOwnerSignup(null, "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", storeId);
        return store;
    }


    private Product createProduct(Long productId, Store store, BigDecimal price, Integer stock, ProductType type) {
        Product product = Product.create(store, null, "테스트 상품", null, price, stock, type);
        ReflectionTestUtils.setField(product, "productId", productId);
        return product;
    }

    private EventProduct createEventProduct(Long eventProductId, Product product, EventProductStatus status) {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        EventProduct ep = EventProduct.create(product, BigDecimal.valueOf(8000), 10, start, end);
        ReflectionTestUtils.setField(ep, "eventProductId", eventProductId);
        ReflectionTestUtils.setField(ep, "status", status);
        return ep;
    }

    private EventProductRequestDto createRequest(Long productId, int eventPrice, int eventStock,
                                                  LocalDateTime startAt, LocalDateTime endAt) {
        EventProductRequestDto dto = new EventProductRequestDto();
        ReflectionTestUtils.setField(dto, "productId", productId);
        ReflectionTestUtils.setField(dto, "eventPrice", eventPrice);
        ReflectionTestUtils.setField(dto, "eventStock", eventStock);
        ReflectionTestUtils.setField(dto, "startAt", startAt);
        ReflectionTestUtils.setField(dto, "endAt", endAt);
        return dto;
    }

    // ──────────────────── getMyEventProducts ────────────────────

    @Test
    void 내_이벤트_상품_목록_조회_성공() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);
        EventProductResponseDto responseDto = EventProductResponseDto.builder()
                .eventProductId(1L).productId(1L).build();

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findByProduct_Store_StoreIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(ep));
        when(productMapper.toEventProductResponseDto(ep)).thenReturn(responseDto);

        // when
        List<EventProductResponseDto> result = eventProductService.getMyEventProducts(100L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventProductId()).isEqualTo(1L);
    }

    @Test
    void 내_이벤트_상품_목록_조회_시_스토어가_없으면_STORE_NOT_FOUND() {
        // given
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventProductService.getMyEventProducts(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    // ──────────────────── createEventProduct ────────────────────

    @Test
    void 이벤트_상품_등록_성공() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProductResponseDto responseDto = EventProductResponseDto.builder().eventProductId(1L).build();

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, end);

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                eq(1L), eq(EventProductStatus.ACTIVE), any())).thenReturn(false);
        when(eventProductRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productMapper.toEventProductResponseDto(any())).thenReturn(responseDto);

        // when
        EventProductResponseDto result = eventProductService.createEventProduct(100L, request);

        // then
        assertThat(result.getEventProductId()).isEqualTo(1L);
        verify(eventProductRepository).save(any(EventProduct.class));
    }

    @Test
    void 이벤트_상품_등록_시_스토어가_없으면_STORE_NOT_FOUND() {
        // given
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, start.plusHours(1));
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 이벤트_상품_등록_시_상품이_없으면_PRODUCT_NOT_FOUND() {
        // given
        Store store = createStore(1L);
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(99L, 8000, 30, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 이벤트_상품_등록_시_다른_스토어_상품이면_EVENT_NOT_FOUND() {
        // given
        Store ownerStore = createStore(1L);
        Store otherStore = createStore(2L);
        Product product = createProduct(1L, otherStore, BigDecimal.valueOf(10000), 100, ProductType.SALE);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(ownerStore));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 이벤트_상품_등록_시_비활성_상품이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        ReflectionTestUtils.setField(product, "status", ProductStatus.INACTIVE);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_등록_시_MENU_타입이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.MENU);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_등록_시_이미_활성_이벤트가_있으면_EVENT_ALREADY_ACTIVE() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                eq(1L), eq(EventProductStatus.ACTIVE), any())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ALREADY_ACTIVE);
    }

    @Test
    void 이벤트_상품_등록_시_이벤트_가격이_원가_이상이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 10000, 30, start, start.plusHours(1)); // 이벤트가격 == 원가

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                eq(1L), eq(EventProductStatus.ACTIVE), any())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_등록_시_이벤트_재고가_0이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 0, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                eq(1L), eq(EventProductStatus.ACTIVE), any())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_등록_시_이벤트_재고가_상품_재고_초과면_EVENT_OUT_OF_STOCK() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 10, ProductType.SALE); // stock=10

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 20, start, start.plusHours(1)); // eventStock=20

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                eq(1L), eq(EventProductStatus.ACTIVE), any())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_OUT_OF_STOCK);
    }

    @Test
    void 이벤트_상품_등록_시_상품_재고가_null이면_이벤트_재고_초과_검증_안함() {
        // given - stock=null은 무제한 재고 상품 → eventStock이 아무리 커도 EVENT_OUT_OF_STOCK 미발생
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), null, ProductType.SALE);
        EventProductResponseDto responseDto = EventProductResponseDto.builder().eventProductId(1L).build();

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 8000, 9999, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(eventProductRepository.existsByProduct_ProductIdAndStatusAndEndAtAfter(
                eq(1L), eq(EventProductStatus.ACTIVE), any())).thenReturn(false);
        when(eventProductRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productMapper.toEventProductResponseDto(any())).thenReturn(responseDto);

        // when — 예외 없이 정상 등록
        EventProductResponseDto result = eventProductService.createEventProduct(100L, request);

        // then
        assertThat(result).isNotNull();
        verify(eventProductRepository).save(any(EventProduct.class));
    }

    @Test
    void 이벤트_상품_등록_시_시작일이_과거이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);

        LocalDateTime start = LocalDateTime.now().minusHours(1); // 과거 시작일
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, start.plusHours(3));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_등록_시_시작일이_종료일_이후이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);

        LocalDateTime start = LocalDateTime.now().plusHours(3);
        LocalDateTime end = LocalDateTime.now().plusHours(1); // end < start
        EventProductRequestDto request = createRequest(1L, 8000, 30, start, end);

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> eventProductService.createEventProduct(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    // ──────────────────── updateEventProduct ────────────────────

    @Test
    void 이벤트_상품_수정_성공() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);
        EventProductResponseDto responseDto = EventProductResponseDto.builder().eventProductId(1L).build();

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusHours(3);
        EventProductRequestDto request = createRequest(1L, 7000, 20, start, end);

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findById(1L)).thenReturn(Optional.of(ep));
        when(productMapper.toEventProductResponseDto(ep)).thenReturn(responseDto);

        // when
        EventProductResponseDto result = eventProductService.updateEventProduct(100L, 1L, request);

        // then
        assertThat(result.getEventProductId()).isEqualTo(1L);
    }

    @Test
    void 이벤트_상품_수정_시_이벤트_상품이_없으면_EVENT_NOT_FOUND() {
        // given
        Store store = createStore(1L);
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(1L, 7000, 20, start, start.plusHours(1));

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventProductService.updateEventProduct(100L, 99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 이벤트_상품_수정_시_productId_불일치면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        EventProductRequestDto request = createRequest(99L, 7000, 20, start, start.plusHours(1)); // 다른 productId

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findById(1L)).thenReturn(Optional.of(ep));

        // when & then
        assertThatThrownBy(() -> eventProductService.updateEventProduct(100L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_수정_시_종료일이_현재보다_과거면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);

        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = LocalDateTime.now().minusMinutes(30); // endAt이 이미 과거
        EventProductRequestDto request = createRequest(1L, 7000, 20, start, end);

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findById(1L)).thenReturn(Optional.of(ep));

        // when & then
        assertThatThrownBy(() -> eventProductService.updateEventProduct(100L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    // ──────────────────── endExpiredEventProducts ────────────────────

    @Test
    void 만료_이벤트_상품_일괄_종료_성공() {
        // given
        when(eventProductRepository.bulkEndExpiredEvents(
                eq(EventProductStatus.ACTIVE), eq(EventProductStatus.ENDED), any())).thenReturn(3);

        // when
        int count = eventProductService.endExpiredEventProducts();

        // then
        assertThat(count).isEqualTo(3);
        verify(eventProductRepository).bulkEndExpiredEvents(
                eq(EventProductStatus.ACTIVE), eq(EventProductStatus.ENDED), any());
    }

    @Test
    void 만료된_이벤트가_없으면_0_반환() {
        // given
        when(eventProductRepository.bulkEndExpiredEvents(
                eq(EventProductStatus.ACTIVE), eq(EventProductStatus.ENDED), any())).thenReturn(0);

        // when
        int count = eventProductService.endExpiredEventProducts();

        // then
        assertThat(count).isEqualTo(0);
    }

    // ──────────────────── endEventProduct ────────────────────

    @Test
    void 이벤트_상품_수동_종료_성공() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);

        when(eventProductRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(ep));
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));

        // when
        eventProductService.endEventProduct(100L, 1L);

        // then
        assertThat(ep.getStatus()).isEqualTo(EventProductStatus.ENDED);
    }

    @Test
    void 이벤트_상품_수동_종료_시_이벤트가_없으면_EVENT_NOT_FOUND() {
        // given
        when(eventProductRepository.findByIdWithPessimisticLock(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventProductService.endEventProduct(100L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 이벤트_상품_수동_종료_시_소유권이_없으면_EVENT_NOT_FOUND() {
        // given
        Store ownerStore = createStore(1L);
        Store otherStore = createStore(2L);
        Product product = createProduct(1L, otherStore, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);

        when(eventProductRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(ep));
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(ownerStore));

        // when & then
        assertThatThrownBy(() -> eventProductService.endEventProduct(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    // [시나리오 4] 이미 ENDED/DELETED 상태인 이벤트에 수동 종료 재시도 (멱등성)
    @Test
    void 이벤트_상품_수동_종료_시_이미_ENDED_상태이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ENDED);

        when(eventProductRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(ep));
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));

        // when & then
        assertThatThrownBy(() -> eventProductService.endEventProduct(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 이벤트_상품_수동_종료_시_이미_DELETED_상태이면_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.DELETED);

        when(eventProductRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(ep));
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));

        // when & then
        assertThatThrownBy(() -> eventProductService.endEventProduct(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    // [시나리오 5] 스케줄러가 먼저 bulk update로 ENDED 처리 → 수동 종료 시 이미 ACTIVE 아님
    @Test
    void 스케줄러_선처리_후_수동_종료_시도_시_COMMON_INVALID_PARAMETER() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        // 스케줄러가 이미 ENDED로 전환한 상태
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ENDED);

        when(eventProductRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(ep));
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));

        // when & then
        assertThatThrownBy(() -> eventProductService.endEventProduct(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    // ──────────────────── deleteEventProduct ────────────────────

    @Test
    void 이벤트_상품_비활성화_성공() {
        // given
        Store store = createStore(1L);
        Product product = createProduct(1L, store, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct ep = createEventProduct(1L, product, EventProductStatus.ACTIVE);

        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findById(1L)).thenReturn(Optional.of(ep));

        // when
        eventProductService.deleteEventProduct(100L, 1L);

        // then
        assertThat(ep.getStatus()).isEqualTo(EventProductStatus.DELETED);
    }

    @Test
    void 이벤트_상품_비활성화_시_이벤트가_없으면_EVENT_NOT_FOUND() {
        // given
        Store store = createStore(1L);
        when(storeRepository.findByAccount_AccountId(anyLong())).thenReturn(Optional.of(store));
        when(eventProductRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventProductService.deleteEventProduct(100L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }
}
