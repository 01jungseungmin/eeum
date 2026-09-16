package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.EeumApplication;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Cart;
import com.eeum.eeum.domain.order.entity.CartItem;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 상품별 판매 수량 집계 검증.
 *
 * 단위 테스트로는 고정할 수 없다 — 이벤트 판매분을 원본 상품으로 되돌리는 coalesce 조인과
 * INACTIVE 제외 필터는 실제 SQL이 돌아야 드러난다.
 */
@EnabledIfDockerAvailable
@SpringBootTest(classes = EeumApplication.class)
@RequiredArgsConstructor
class OrderItemProductSalesIntegrationTest extends IntegrationTestSupport {

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private Long storeId;
    private Long americanoId;
    private Long latteId;
    private Long deletedProductId;

    private Account buyer;
    private Store store;
    private int orderSequence;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(Account.createOwner(
                "sales-owner@test.com", "encoded_pw", "판매 통계 사장", "010-3333-3333"));
        buyer = accountRepository.save(Account.createUser(
                "sales-buyer@test.com", "encoded_pw", "구매자", "구매자닉", "010-4444-4444"));

        store = storeRepository.save(Store.createForOwnerSignup(
                owner, "판매 통계 상점", "서울시 강남구", "02-3333-3333"));
        storeId = store.getStoreId();

        ProductCategory category = productCategoryRepository.save(
                ProductCategory.create(store, "음료", 1));

        Product americano = productRepository.save(Product.create(
                store, category, "아메리카노", null, new BigDecimal("4000"), 100, ProductType.MENU));
        Product latte = productRepository.save(Product.create(
                store, category, "라떼", null, new BigDecimal("5000"), 100, ProductType.MENU));
        Product deleted = productRepository.save(Product.create(
                store, category, "단종된 메뉴", null, new BigDecimal("3000"), 100, ProductType.MENU));
        deleted.deactivate();
        productRepository.save(deleted);

        americanoId = americano.getProductId();
        latteId = latte.getProductId();
        deletedProductId = deleted.getProductId();

        EventProduct americanoEvent = eventProductRepository.save(EventProduct.create(
                americano,
                new BigDecimal("3000"),
                50,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)));

        // 아메리카노: 일반 2개 + 이벤트 3개 = 5개로 합산되어야 한다
        saveCompletedOrder(productItem(americano, 2), eventItem(americanoEvent, 3));
        saveCompletedOrder(productItem(latte, 4));
        saveOrder(OrderStatus.CANCELLED, productItem(latte, 100));
        saveCompletedOrder(productItem(deleted, 7));

    }

    @AfterEach
    void cleanup() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        eventProductRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productCategoryRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
    }

    @Test
    void 이벤트_판매분은_원본_상품에_합산되고_삭제된_상품은_제외된다() {
        List<ProductSalesQuantity> result = orderItemRepository.aggregateSoldQuantityByProduct(
                storeId, List.of(OrderStatus.COMPLETED), null, null);

        assertThat(result)
                .extracting(ProductSalesQuantity::productId, ProductSalesQuantity::soldQuantity)
                .containsExactly(
                        tuple(americanoId, 5L),
                        tuple(latteId, 4L));
        assertThat(result)
                .extracting(ProductSalesQuantity::productId)
                .doesNotContain(deletedProductId);
    }

    @Test
    void 지정한_주문_상태만_집계한다() {
        List<ProductSalesQuantity> result = orderItemRepository.aggregateSoldQuantityByProduct(
                storeId, List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED), null, null);

        assertThat(result)
                .extracting(ProductSalesQuantity::productId, ProductSalesQuantity::soldQuantity)
                .containsExactly(
                        tuple(latteId, 104L),
                        tuple(americanoId, 5L));
    }

    @Test
    void 기간_밖의_주문은_집계에서_빠진다() {
        List<ProductSalesQuantity> result = orderItemRepository.aggregateSoldQuantityByProduct(
                storeId, List.of(OrderStatus.COMPLETED),
                LocalDateTime.now().plusDays(1), null);

        assertThat(result).isEmpty();
    }

    private CartItem productItem(Product product, int quantity) {
        return CartItem.createForProduct(
                Cart.create(buyer), product, null, null, BigDecimal.ZERO, "none", quantity);
    }

    private CartItem eventItem(EventProduct eventProduct, int quantity) {
        return CartItem.createForEventProduct(Cart.create(buyer), eventProduct, quantity);
    }

    private void saveCompletedOrder(CartItem... items) {
        saveOrder(OrderStatus.COMPLETED, items);
    }

    private void saveOrder(OrderStatus status, CartItem... items) {
        Order order = Order.create(
                buyer, store, new BigDecimal("10000"),
                "SALES-TEST-" + (++orderSequence), OrderType.SALE, null, null);
        if (status == OrderStatus.COMPLETED) {
            order.markAsPaid();
            order.confirm();
            order.ready();
            order.complete();
        } else {
            order.cancel("테스트");
        }
        Order saved = orderRepository.save(order);

        for (CartItem item : items) {
            orderItemRepository.save(OrderItem.createFromCartItem(saved, item, null));
        }
    }
}
