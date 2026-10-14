package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.service.OrderService;
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
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비활성화가 먼저 완료돼도 이전 조회 스냅샷으로 주문되는 증상을 검증한다.
 * 결함 위치: OrderService.isActiveProductCategory와 ProductCategoryService.deactivateCategory.
 * 주문이 카테고리를 읽은 뒤 별도 트랜잭션이 비활성화하면 캐시와 RR 스냅샷이 낡는다.
 * 카테고리 변경이 먼저 커밋된 주문은 재고를 차감하지 않고 거절해야 한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ProductCategoryOrderConcurrencyIntegrationTest extends IntegrationTestSupport {

    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductCategoryService categoryService;
    private final OrderService orderService;
    private final TransactionTemplate transactionTemplate;

    private Long ownerId;
    private Long customerId;
    private Long categoryId;
    private Long productId;
    private Long cartId;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(Account.createOwner(
                "category-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        ownerId = owner.getAccountId();
        OwnerInfo ownerInfo = OwnerInfo.create(owner, "123-45-67890", LocalDate.of(2020, 1, 1));
        ownerInfo.approve();
        ownerInfoRepository.save(ownerInfo);

        Store store = Store.createForOwnerSignup(owner, "카테고리 상점", "서울시", "02-0000-0000");
        store.open();
        store = storeRepository.save(store);
        ProductCategory category = categoryRepository.save(ProductCategory.create(store, "판매 카테고리", 0));
        categoryId = category.getProductCategoryId();
        Product product = productRepository.save(Product.create(store, category, "상품", null,
                BigDecimal.valueOf(10000), 3, ProductType.SALE));
        productId = product.getProductId();

        Account customer = accountRepository.save(Account.createUser(
                "category-customer@test.com", "encoded_pw", "고객", "닉네임", "010-2222-2222"));
        customerId = customer.getAccountId();
        Cart cart = Cart.create(customer);
        cart.updateStore(store);
        cart = cartRepository.save(cart);
        cartId = cart.getCartId();
        cartItemRepository.save(CartItem.createForProduct(
                cart, product, null, null, BigDecimal.ZERO, "none", 1));
    }

    @AfterEach
    void tearDown() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        storeRepository.deleteAll();
        ownerInfoRepository.deleteAll();
    }

    @Test
    void 카테고리_비활성화가_먼저_커밋되면_기존_장바구니_주문을_거절한다() {
        OrderCreateRequestDto request = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(request, "paymentMethod", PaymentMethod.EASY_PAY);
        AtomicReference<Throwable> orderFailure = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(cartItemRepository.findByCart_CartId(cartId)).hasSize(1);
            assertThat(categoryRepository.findById(categoryId).orElseThrow().isActive()).isTrue();

            CompletableFuture<Void> deactivation = CompletableFuture.runAsync(
                    () -> categoryService.deactivateCategory(ownerId, categoryId));
            try {
                deactivation.get(10, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError("카테고리 비활성화가 먼저 완료돼야 한다", failure);
            }

            try {
                orderService.createOrder(customerId, request);
            } catch (Throwable failure) {
                orderFailure.set(failure);
            }
            status.setRollbackOnly();
        });

        assertThat(orderFailure.get()).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) orderFailure.get()).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(3);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void 카테고리_비활성화는_상점_행_잠금이_해제된_뒤_진행한다() throws Exception {
        AtomicReference<CompletableFuture<Void>> deactivation = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            storeRepository.findByAccountIdWithPessimisticLock(ownerId).orElseThrow();
            deactivation.set(CompletableFuture.runAsync(
                    () -> categoryService.deactivateCategory(ownerId, categoryId)));
            try {
                assertThat(awaitLockWait("store")).isTrue();
            } catch (Exception failure) {
                throw new AssertionError("상점 행 잠금 대기를 관측해야 한다", failure);
            }
            assertThat(categoryRepository.findById(categoryId).orElseThrow().isActive()).isTrue();
        });

        deactivation.get().get(10, TimeUnit.SECONDS);
        assertThat(categoryRepository.findById(categoryId).orElseThrow().isActive()).isFalse();
    }
}
