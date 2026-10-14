package com.eeum.eeum.application.product.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.application.store.service.PublicStoreService;
import com.eeum.eeum.application.store.dto.request.StoreSearchDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.order.entity.Cart;
import com.eeum.eeum.domain.order.entity.CartItem;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.repository.CartItemRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트 수동 종료 vs 주문 생성 동시 요청 — 실제 MySQL/Redis Testcontainer 환경에서 실행.
 *
 * <p><b>기대 동작:</b>
 * <ol>
 *   <li>endEventProduct: PESSIMISTIC_WRITE 락 획득 → ACTIVE 확인 → ENDED 처리</li>
 *   <li>createOrder: 동일 이벤트 상품에 PESSIMISTIC_WRITE 락 시도 → 선착순 처리</li>
 *   <li>결과: 두 작업 중 하나 이상 성공, 실패 시 올바른 ErrorCode 반환</li>
 * </ol>
 *
 * <p><b>시나리오 A</b> (endEventProduct 선점): 종료 성공, 주문은 EVENT_NOT_FOUND<br>
 * <b>시나리오 B</b> (createOrder 선점): 주문 성공, 이후 종료도 성공 (순차 처리)
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class EventProductConcurrencyIntegrationTest extends IntegrationTestSupport {



    private final EventProductService eventProductService;
    private final OrderService orderService;
    private final PublicStoreService publicStoreService;
    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final EventProductRepository eventProductRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    private Long ownerAccountId;
    private Long customerAccountId;
    private Long eventProductId;
    private Long productId;

    @BeforeEach
    void setUp() {
        Account owner = Account.createOwner("owner@test.com", "encoded_pw", "점주", "010-1111-1111");
        owner = accountRepository.save(owner);
        ownerAccountId = owner.getAccountId();
        OwnerInfo ownerInfo = OwnerInfo.create(owner, "123-45-67890", LocalDate.of(2020, 1, 1));
        ownerInfo.approve();
        ownerInfoRepository.save(ownerInfo);

        Store store = Store.createForOwnerSignup(owner, "테스트 상점", "서울시", "02-0000-0000");
        // 주문 생성은 상점이 OPEN일 때만 통과한다 — 가입 직후 상태(TEMP_CLOSED)로 두면
        // 이 테스트가 보려는 이벤트 종료 경쟁 대신 STORE_CLOSED로 끝난다
        store.open();
        store = storeRepository.save(store);

        ProductCategory category = ProductCategory.create(store, "이벤트 카테고리", 0);
        category = productCategoryRepository.save(category);

        Product product = Product.create(store, category, "이벤트 상품", null,
                BigDecimal.valueOf(10000), 100, ProductType.SALE);
        product = productRepository.save(product);
        productId = product.getProductId();

        EventProduct eventProduct = EventProduct.create(
                product,
                BigDecimal.valueOf(8000),
                10,
                LocalDateTime.now().minusHours(1),   // startAt: 1시간 전 (진행 중)
                LocalDateTime.now().plusHours(1)      // endAt: 1시간 후 (진행 중)
        );
        eventProduct = eventProductRepository.save(eventProduct);
        eventProductId = eventProduct.getEventProductId();

        Account customer = Account.createUser(
                "customer@test.com", "encoded_pw", "고객", "닉네임", "010-2222-2222");
        customer = accountRepository.save(customer);
        customerAccountId = customer.getAccountId();

        Cart cart = Cart.create(customer);
        cart.updateStore(store);
        cart = cartRepository.save(cart);

        CartItem cartItem = CartItem.createForEventProduct(cart, eventProduct, 1);
        cartItemRepository.save(cartItem);
    }


    @AfterEach
    void tearDown() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        eventProductRepository.deleteAll();
        productRepository.deleteAll();
        productCategoryRepository.deleteAll();
        storeRepository.deleteAll();
        ownerInfoRepository.deleteAll();
    }

    @Test
    void 공개_상점_목록은_실제_고정_정렬을_응답한다() {
        var page = storeRepository.searchStores(new StoreSearchDto(),
                PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getSort().toList()).containsExactly(
                Sort.Order.asc("statusPriority"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("storeId"));
    }

    @Test
    void 이벤트_주문은_일반_상품과_공유하는_실재고를_차감한다() {
        // OrderService.validateAndDecreaseStock: 이벤트 판매 수량만 늘리면 일반 주문이
        // 같은 Product.stock을 다시 사용할 수 있어 실재고보다 많이 판매된다.
        orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY));

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(99);
        assertThat(eventProductRepository.findById(eventProductId).orElseThrow().getSoldCount()).isEqualTo(1);
    }

    @Test
    void 이벤트_주문_취소_복원은_실재고와_이벤트_판매수량을_함께_되돌린다() {
        orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY));
        Long orderId = orderRepository.findAll().get(0).getOrderId();

        orderService.restoreStockForOrder(orderId);

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(100);
        assertThat(eventProductRepository.findById(eventProductId).orElseThrow().getSoldCount()).isZero();
    }

    @Test
    void 이벤트_재고_복원과_일반_주문이_겹쳐도_복원량이_사라지지_않는다() throws InterruptedException {
        orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY));
        Long orderId = orderRepository.findAll().get(0).getOrderId();
        Product product = productRepository.findById(productId).orElseThrow();

        Account other = accountRepository.save(Account.createUser(
                "restore-race-customer@test.com", "encoded_pw", "복원고객", "복원고객", "010-4444-4444"));
        Cart regularCart = Cart.create(other);
        regularCart.updateStore(product.getStore());
        cartRepository.save(regularCart);
        cartItemRepository.save(CartItem.createForProduct(
                regularCart, product, null, null, BigDecimal.ZERO, "none", 1));

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> restoreError = new AtomicReference<>();
        AtomicReference<Throwable> regularError = new AtomicReference<>();
        AtomicBoolean regularSuccess = new AtomicBoolean();
        new Thread(() -> {
            try {
                start.await();
                orderService.restoreStockForOrder(orderId);
            } catch (Throwable error) {
                restoreError.set(error);
            } finally {
                done.countDown();
            }
        }).start();
        new Thread(() -> {
            try {
                start.await();
                orderService.createOrder(other.getAccountId(), buildOrderRequest(PaymentMethod.EASY_PAY));
                regularSuccess.set(true);
            } catch (Throwable error) {
                regularError.set(error);
            } finally {
                done.countDown();
            }
        }).start();
        start.countDown();

        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(restoreError.get()).isNull();
        if (regularError.get() != null) {
            assertThat(regularError.get()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) regularError.get()).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        assertThat(productRepository.findById(productId).orElseThrow().getStock())
                .isEqualTo(regularSuccess.get() ? 99 : 100);
        assertThat(eventProductRepository.findById(eventProductId).orElseThrow().getSoldCount()).isZero();
    }

    @Test
    void 일반_주문과_이벤트_주문이_마지막_실재고를_동시에_팔지_못한다() throws InterruptedException {
        Product product = productRepository.findById(productId).orElseThrow();
        product.updateStock(1);
        productRepository.saveAndFlush(product);

        Account other = accountRepository.save(Account.createUser(
                "second-customer@test.com", "encoded_pw", "고객2", "고객2", "010-3333-3333"));
        Cart regularCart = Cart.create(other);
        regularCart.updateStore(product.getStore());
        cartRepository.save(regularCart);
        cartItemRepository.save(CartItem.createForProduct(
                regularCart, product, null, null, BigDecimal.ZERO, "none", 1));

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> eventError = new AtomicReference<>();
        AtomicReference<Throwable> regularError = new AtomicReference<>();
        AtomicBoolean eventSuccess = new AtomicBoolean();
        AtomicBoolean regularSuccess = new AtomicBoolean();
        Thread eventThread = new Thread(() -> {
            try {
                start.await();
                orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY));
                eventSuccess.set(true);
            } catch (Throwable error) {
                eventError.set(error);
            } finally {
                done.countDown();
            }
        });
        Thread regularThread = new Thread(() -> {
            try {
                start.await();
                orderService.createOrder(other.getAccountId(), buildOrderRequest(PaymentMethod.EASY_PAY));
                regularSuccess.set(true);
            } catch (Throwable error) {
                regularError.set(error);
            } finally {
                done.countDown();
            }
        });
        eventThread.start();
        regularThread.start();
        start.countDown();

        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(eventSuccess.get() ^ regularSuccess.get()).isTrue();
        Throwable rejected = eventSuccess.get() ? regularError.get() : eventError.get();
        assertThat(rejected).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) rejected).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
    }

    @Test
    void 서로_다른_조회자들의_동시_상품_조회는_낙관적_잠금_충돌_없이_모두_반영된다()
            throws InterruptedException {
        // PublicStoreService.increaseViewCountIfFirstView: 조회수는 원자 UPDATE로 증가하며
        // Product.version을 건드리는 읽기 요청끼리 충돌해서는 안 된다.
        int viewers = 12;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(viewers);
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (int i = 0; i < viewers; i++) {
            String viewerKey = "stage4-viewer-" + i;
            new Thread(() -> {
                try {
                    start.await();
                    publicStoreService.getProductDetail(productId, viewerKey);
                } catch (Throwable failure) {
                    error.compareAndSet(null, failure);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        assertThat(productRepository.findById(productId).orElseThrow().getViewCount()).isEqualTo(viewers);
    }

    @Test
    void 한_장바구니의_동일_상품_두_항목은_실재고를_두_번_차감한다() {
        cartItemRepository.deleteAll();
        Cart cart = cartRepository.findAll().get(0);
        Product product = productRepository.findById(productId).orElseThrow();
        product.updateStock(2);
        productRepository.saveAndFlush(product);
        cartItemRepository.save(CartItem.createForProduct(
                cart, product, null, null, BigDecimal.ZERO, "option-a", 1));
        cartItemRepository.save(CartItem.createForProduct(
                cart, product, null, null, BigDecimal.ZERO, "option-b", 1));

        orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY));

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
    }

    @Test
    void 비활성화된_상품의_기존_이벤트_장바구니로는_주문할_수_없다() {
        // OrderService.validateAndDecreaseStock: 이벤트 상태만 확인하면 부모 상품을
        // 비활성화한 뒤에도 기존 장바구니에서 새 유료 주문이 생성된다.
        Product product = productRepository.findById(productId).orElseThrow();
        product.deactivate();
        productRepository.saveAndFlush(product);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }

    @Test
    void 비활성화된_카테고리의_기존_이벤트_장바구니로는_주문할_수_없다() {
        ProductCategory category = productCategoryRepository.findAll().get(0);
        category.deactivate();
        productCategoryRepository.saveAndFlush(category);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }

    @Test
    void 승인이_철회된_사장의_기존_장바구니로는_주문할_수_없다() {
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccount_AccountId(ownerAccountId).orElseThrow();
        ownerInfo.reject("관리자 조치");
        ownerInfoRepository.saveAndFlush(ownerInfo);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                orderService.createOrder(customerAccountId, buildOrderRequest(PaymentMethod.EASY_PAY)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 이벤트_수동_종료와_주문_생성_동시_요청_중_하나_이상_성공한다() throws InterruptedException {
        // given
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(2);

        AtomicBoolean endSuccess   = new AtomicBoolean(false);
        AtomicBoolean orderSuccess = new AtomicBoolean(false);
        AtomicReference<Throwable> endError   = new AtomicReference<>();
        AtomicReference<Throwable> orderError = new AtomicReference<>();

        Thread endThread = new Thread(() -> {
            try {
                startLatch.await();
                eventProductService.endEventProduct(ownerAccountId, eventProductId);
                endSuccess.set(true);
            } catch (Exception e) {
                endError.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        Thread orderThread = new Thread(() -> {
            try {
                startLatch.await();
                OrderCreateRequestDto request = buildOrderRequest(PaymentMethod.EASY_PAY);
                orderService.createOrder(customerAccountId, request);
                orderSuccess.set(true);
            } catch (Exception e) {
                orderError.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        endThread.start();
        orderThread.start();
        startLatch.countDown(); // 두 스레드 동시 출발

        // when
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);

        // then
        assertThat(completed).isTrue();

        assertThat(endSuccess.get() || orderSuccess.get())
                .as("수동 종료 또는 주문 생성 중 하나 이상 성공해야 한다")
                .isTrue();

        // [시나리오 A] 주문 생성이 실패했다면 EVENT_NOT_FOUND
        if (!orderSuccess.get() && orderError.get() != null) {
            assertThat(orderError.get()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) orderError.get()).getErrorCode())
                    .as("주문 생성 실패 시 EVENT_NOT_FOUND")
                    .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
        }

        // [시나리오 B] 수동 종료가 실패했다면 COMMON_INVALID_PARAMETER (이미 ACTIVE 아님)
        if (!endSuccess.get() && endError.get() != null) {
            assertThat(endError.get()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) endError.get()).getErrorCode())
                    .as("수동 종료 실패 시 COMMON_INVALID_PARAMETER")
                    .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private OrderCreateRequestDto buildOrderRequest(PaymentMethod paymentMethod) {
        OrderCreateRequestDto dto = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(dto, "paymentMethod", paymentMethod);
        ReflectionTestUtils.setField(dto, "pickupScheduledAt", null);
        ReflectionTestUtils.setField(dto, "requestMessage", null);
        return dto;
    }
}
