package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.CartItemAddRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Cart;
import com.eeum.eeum.domain.order.repository.CartItemRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 사용자의 장바구니 쓰기가 실제 행 잠금으로 직렬화되는지 고정한다.
 *
 * 첫 요청이 카트를 만들면 두 번째 요청의 카트 잠금 조회가 그 행을 기다린다. 카트가 있으면 카트 행을 잠근다.
 * 어느 경우든 두 번째 요청이 잠금을 실제로 기다린 뒤, 중복 카트 없이 두 요청이 모두 반영돼야 한다.
 * 동시에 출발한 첫 담기는 잠글 행이 없는 구간을 함께 지나므로 따로 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class CartConcurrencyIntegrationTest extends IntegrationTestSupport {

    private final CartService cartService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PlatformTransactionManager transactionManager;

    private Account buyer;
    private Product bread;
    private Product milk;
    private Product jam;
    private Product otherStoreTea;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(Account.createOwner(
                "cart-race-owner@test.com", "encoded_pw", "카트 사장", "010-7200-0001"));
        buyer = accountRepository.save(Account.createUser(
                "cart-race-buyer@test.com", "encoded_pw", "구매자", "카트경쟁구매자", "010-7200-0002"));
        Store store = storeRepository.save(Store.createForOwnerSignup(
                owner, "카트 경쟁 상점", "서울시 마포구", "02-7200-0001"));
        ProductCategory category = productCategoryRepository.save(ProductCategory.create(store, "베이커리", 1));
        bread = productRepository.save(Product.create(
                store, category, "식빵", null, new BigDecimal("5000"), 100, ProductType.SALE));
        milk = productRepository.save(Product.create(
                store, category, "우유", null, new BigDecimal("2000"), 100, ProductType.SALE));
        jam = productRepository.save(Product.create(
                store, category, "잼", null, new BigDecimal("3000"), 100, ProductType.SALE));
        Account otherOwner = accountRepository.save(Account.createOwner(
                "cart-race-other-owner@test.com", "encoded_pw", "다른 카트 사장", "010-7200-0003"));
        Store otherStore = storeRepository.save(Store.createForOwnerSignup(
                otherOwner, "다른 카트 경쟁 상점", "서울시 성동구", "02-7200-0002"));
        ProductCategory otherCategory = productCategoryRepository.save(ProductCategory.create(otherStore, "음료", 1));
        otherStoreTea = productRepository.save(Product.create(
                otherStore, otherCategory, "차", null, new BigDecimal("4000"), 100, ProductType.SALE));
    }

    @AfterEach
    void cleanup() {
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productCategoryRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
    }

    @Test
    void 카트가_없는_사용자가_동시에_담아도_카트는_하나만_생기고_두_상품이_모두_담긴다() throws Exception {
        // given
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        // when — 첫 요청이 카트를 만들고 커밋하기 전에 두 번째 요청이 들어온다
        raceOnLock(transactionManager, "cart",
                () -> cartService.addItem(buyer.getAccountId(), addRequest(bread)),
                () -> cartService.addItem(buyer.getAccountId(), addRequest(milk)),
                secondFailure);

        // then
        assertThat(secondFailure.get()).isNull();
        Cart cart = cartRepository.findByAccount_AccountId(buyer.getAccountId()).orElseThrow();
        assertThat(cartRepository.findAll())
                .filteredOn(c -> c.getAccount().getAccountId().equals(buyer.getAccountId()))
                .hasSize(1);
        assertThat(cartItemRepository.findByCart_CartId(cart.getCartId())).hasSize(2);
    }

    @Test
    void 카트가_있으면_동시_담기가_카트_행_잠금으로_직렬화되어_모두_반영된다() throws Exception {
        // given
        cartService.addItem(buyer.getAccountId(), addRequest(bread));
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        // when
        raceOnLock(transactionManager, "cart",
                () -> cartService.addItem(buyer.getAccountId(), addRequest(milk)),
                () -> cartService.addItem(buyer.getAccountId(), addRequest(jam)),
                secondFailure);

        // then
        assertThat(secondFailure.get()).isNull();
        Cart cart = cartRepository.findByAccount_AccountId(buyer.getAccountId()).orElseThrow();
        assertThat(cartItemRepository.findByCart_CartId(cart.getCartId())).hasSize(3);
    }

    @Test
    void 카트가_없는_사용자의_첫_담기가_동시에_출발해도_모두_성공하고_카트는_하나다() throws Exception {
        // 교착은 타이밍에 따라 나타나므로 한 번 통과로 판단하지 않고 여러 번 반복한다.
        List<Product> products = List.of(bread, milk, jam);
        for (int round = 1; round <= 5; round++) {
            // given — 매 회차 카트가 없는 상태에서 시작한다
            cartItemRepository.deleteAllInBatch();
            cartRepository.deleteAllInBatch();

            // when
            List<Throwable> failures = addConcurrently(products);

            // then
            assertThat(failures).as("round %d", round).isEmpty();
            Cart cart = cartRepository.findByAccount_AccountId(buyer.getAccountId()).orElseThrow();
            assertThat(cartItemRepository.findByCart_CartId(cart.getCartId()))
                    .as("round %d", round)
                    .hasSize(products.size());
        }
    }

    @Test
    void 서로_다른_상점_상품을_동시에_담아도_카트에는_한_상점_상품만_남는다() throws Exception {
        // 원래 C7의 경쟁: 두 요청 모두 빈 카트를 읽더라도, 행 잠금 뒤의 요청은 앞선 상점 항목을
        // 삭제하고 자기 상점만 남긴다. 어느 요청이 마지막인지는 보장하지 않는다.
        List<Throwable> failures = addConcurrently(List.of(bread, otherStoreTea));

        assertThat(failures).isEmpty();
        Cart cart = cartRepository.findByAccount_AccountId(buyer.getAccountId()).orElseThrow();
        // 다른 트랜잭션에서 읽은 CartItem의 Product는 지연 프록시다. 프록시 초기화가
        // 필요 없는 식별자만 확인해 테스트가 세션 수명에 의존하지 않게 한다.
        List<Long> productIds = cartItemRepository.findByCart_CartId(cart.getCartId()).stream()
                .map(item -> item.getProduct().getProductId())
                .toList();
        assertThat(productIds).hasSize(1);
        assertThat(productIds.get(0)).isIn(bread.getProductId(), otherStoreTea.getProductId());
    }

    private List<Throwable> addConcurrently(List<Product> products) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(products.size());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Product product : products) {
                futures.add(executor.submit(() -> {
                    awaitQuietly(start);
                    try {
                        cartService.addItem(buyer.getAccountId(), addRequest(product));
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return failures;
    }

    private CartItemAddRequestDto addRequest(Product product) {
        CartItemAddRequestDto request = new CartItemAddRequestDto();
        ReflectionTestUtils.setField(request, "productId", product.getProductId());
        ReflectionTestUtils.setField(request, "quantity", 1);
        return request;
    }
}
