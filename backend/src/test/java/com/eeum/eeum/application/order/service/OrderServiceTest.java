package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Cart;
import com.eeum.eeum.domain.order.entity.CartItem;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.repository.CartItemRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductImageRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock private AccountRepository accountRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private EventProductRepository eventProductRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private ApplicationEventPublisher eventPublisher;

    // ──────────────────── Helpers ────────────────────

    // endAt이 과거인 EventProduct — isOngoing() = false
    private EventProduct createExpiredEventProduct(Long id) {
        Store store = Store.createForOwnerSignup(null, "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", 1L);

        Product product = Product.create(store, null, "이벤트 상품", null,
                BigDecimal.valueOf(10000), 100, ProductType.SALE);
        ReflectionTestUtils.setField(product, "productId", 1L);

        EventProduct ep = EventProduct.create(product, BigDecimal.valueOf(8000), 10,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(30)); // endAt이 이미 지남 → isOngoing() = false
        ReflectionTestUtils.setField(ep, "eventProductId", id);
        return ep;
    }

    // ──────────────────── createOrder ────────────────────

    // [시나리오 2] 이벤트 종료 후 카트에 남은 이벤트 상품으로 주문 생성 → EVENT_NOT_FOUND
    @Test
    void 종료된_이벤트_상품으로_주문_생성_시_EVENT_NOT_FOUND() {
        // given
        Long accountId = 100L;
        Long cartId = 1L;
        Long eventProductId = 5L;

        Account account = mock(Account.class);
        Cart cart = mock(Cart.class);
        when(cart.getCartId()).thenReturn(cartId);

        // 이미 endAt이 지난 이벤트 상품 (isOngoing() = false)
        EventProduct expiredEvent = createExpiredEventProduct(eventProductId);

        CartItem cartItem = mock(CartItem.class);
        // product = null → resolveProductType이 SALE 반환 → OrderType.SALE
        when(cartItem.getEventProduct()).thenReturn(expiredEvent);

        OrderCreateRequestDto request = mock(OrderCreateRequestDto.class);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(cartRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_CartId(cartId)).thenReturn(List.of(cartItem));
        // 락 획득 후 조회한 이벤트 상품도 이미 진행 종료 상태
        when(eventProductRepository.findByIdWithPessimisticLock(eventProductId))
                .thenReturn(Optional.of(expiredEvent));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    // [서브케이스] 이벤트 상품이 DB에 아예 없으면 orElseThrow → EVENT_NOT_FOUND
    @Test
    void 이벤트_상품이_DB에_없으면_EVENT_NOT_FOUND() {
        // given
        Long accountId = 100L;
        Long cartId = 1L;
        Long missingEventProductId = 99L;

        Account account = mock(Account.class);
        Cart cart = mock(Cart.class);
        when(cart.getCartId()).thenReturn(cartId);

        // DB에는 없는 이벤트 상품을 가리키는 CartItem
        EventProduct ghostEvent = EventProduct.create(
                Product.create(
                        Store.createForOwnerSignup(null, "상점", "주소", "010-0000-0000"),
                        null, "상품", null, BigDecimal.valueOf(10000), 10, ProductType.SALE),
                BigDecimal.valueOf(8000), 5,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(ghostEvent, "eventProductId", missingEventProductId);

        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getEventProduct()).thenReturn(ghostEvent);

        OrderCreateRequestDto request = mock(OrderCreateRequestDto.class);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(cartRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_CartId(cartId)).thenReturn(List.of(cartItem));
        // 락 기반 조회에서 empty → orElseThrow fires
        when(eventProductRepository.findByIdWithPessimisticLock(missingEventProductId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    // [서브케이스] 이벤트 진행 중이지만 재고 부족 → PRODUCT_OUT_OF_STOCK
    @Test
    void 이벤트_재고_부족_시_PRODUCT_OUT_OF_STOCK() {
        // given
        Long accountId = 100L;
        Long cartId = 1L;
        Long eventProductId = 7L;

        Account account = mock(Account.class);
        Cart cart = mock(Cart.class);
        when(cart.getCartId()).thenReturn(cartId);

        // 진행 중인 이벤트 상품 (endAt 미래) but soldCount = eventStock → remainingStock = 0
        Store store = Store.createForOwnerSignup(null, "상점", "주소", "010-0000-0000");
        Product product = Product.create(store, null, "상품", null,
                BigDecimal.valueOf(10000), 50, ProductType.SALE);
        EventProduct soldOutEvent = EventProduct.create(product, BigDecimal.valueOf(7000), 5,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)); // isOngoing() = true
        // soldCount를 eventStock과 동일하게 설정 → getRemainingStock() = 0
        ReflectionTestUtils.setField(soldOutEvent, "soldCount", 5);
        ReflectionTestUtils.setField(soldOutEvent, "eventProductId", eventProductId);

        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getEventProduct()).thenReturn(soldOutEvent);
        when(cartItem.getQuantity()).thenReturn(1); // 1개 요청, 남은 재고 0

        OrderCreateRequestDto request = mock(OrderCreateRequestDto.class);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(cartRepository.findByAccount_AccountId(accountId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_CartId(cartId)).thenReturn(List.of(cartItem));
        when(eventProductRepository.findByIdWithPessimisticLock(eventProductId))
                .thenReturn(Optional.of(soldOutEvent));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }

    // ──────────────────── restoreStockForOrder ────────────────────

    @Test
    void 재고복구_이벤트상품이면_EventProduct_재고가_복구된다() {
        // given
        Long orderId = 1L;
        Long eventProductId = 5L;

        Store store = Store.createForOwnerSignup(null, "테스트 상점", "서울시", "010-0000-0000");
        Product product = Product.create(store, null, "이벤트 상품", null,
                BigDecimal.valueOf(10000), 100, ProductType.SALE);
        EventProduct eventProduct = EventProduct.create(product, BigDecimal.valueOf(8000), 10,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(eventProduct, "eventProductId", eventProductId);
        ReflectionTestUtils.setField(eventProduct, "soldCount", 3);

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getEventProductId()).thenReturn(eventProductId);
        when(orderItem.getQuantity()).thenReturn(2);

        when(orderItemRepository.findByOrder_OrderId(orderId)).thenReturn(List.of(orderItem));
        when(eventProductRepository.findByIdWithPessimisticLock(eventProductId))
                .thenReturn(Optional.of(eventProduct));

        // when
        orderService.restoreStockForOrder(orderId);

        // then — eventStock(10) - (soldCount 3 - quantity 2 = 1) = 9
        assertThat(eventProduct.getRemainingStock()).isEqualTo(9);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void 재고복구_이벤트상품이_DB에_없으면_복구_스킵() {
        // given
        Long orderId = 1L;
        Long eventProductId = 99L;

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getEventProductId()).thenReturn(eventProductId);

        when(orderItemRepository.findByOrder_OrderId(orderId)).thenReturn(List.of(orderItem));
        when(eventProductRepository.findByIdWithPessimisticLock(eventProductId))
                .thenReturn(Optional.empty());

        // when & then — 예외 없이 스킵
        orderService.restoreStockForOrder(orderId);

        verify(productRepository, never()).findById(any());
    }

    @Test
    void 재고복구_일반상품이면_Product_재고가_복구된다() {
        // given
        Long orderId = 1L;
        Long productId = 7L;

        Store store = Store.createForOwnerSignup(null, "테스트 상점", "서울시", "010-0000-0000");
        Product product = Product.create(store, null, "일반 상품", null,
                BigDecimal.valueOf(5000), 10, ProductType.SALE);
        ReflectionTestUtils.setField(product, "productId", productId);

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getEventProductId()).thenReturn(null);
        when(orderItem.getProductId()).thenReturn(productId);
        when(orderItem.getQuantity()).thenReturn(3);

        when(orderItemRepository.findByOrder_OrderId(orderId)).thenReturn(List.of(orderItem));
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));

        // when
        orderService.restoreStockForOrder(orderId);

        // then
        assertThat(product.getStock()).isEqualTo(13);
    }

    @Test
    void 재고복구_일반상품_재고가_무제한이면_복구_스킵() {
        // given
        Long orderId = 1L;
        Long productId = 7L;

        Store store = Store.createForOwnerSignup(null, "테스트 상점", "서울시", "010-0000-0000");
        Product product = Product.create(store, null, "무제한 재고 상품", null,
                BigDecimal.valueOf(5000), null, ProductType.SALE);
        ReflectionTestUtils.setField(product, "productId", productId);

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getEventProductId()).thenReturn(null);
        when(orderItem.getProductId()).thenReturn(productId);

        when(orderItemRepository.findByOrder_OrderId(orderId)).thenReturn(List.of(orderItem));
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));

        // when
        orderService.restoreStockForOrder(orderId);

        // then — 무제한 재고는 변경 없음
        assertThat(product.getStock()).isNull();
    }
}
