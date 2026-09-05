package com.eeum.eeum.application.product.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final AccountRepository accountRepository;
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

    @BeforeEach
    void setUp() {
        Account owner = Account.createOwner("owner@test.com", "encoded_pw", "점주", "010-1111-1111");
        owner = accountRepository.save(owner);
        ownerAccountId = owner.getAccountId();

        Store store = Store.createForOwnerSignup(owner, "테스트 상점", "서울시", "02-0000-0000");
        store = storeRepository.save(store);

        ProductCategory category = ProductCategory.create(store, "이벤트 카테고리", 0);
        category = productCategoryRepository.save(category);

        Product product = Product.create(store, category, "이벤트 상품", null,
                BigDecimal.valueOf(10000), 100, ProductType.SALE);
        product = productRepository.save(product);

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
