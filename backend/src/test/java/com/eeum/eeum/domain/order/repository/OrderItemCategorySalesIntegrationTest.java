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
 * 카테고리별 판매 집계 검증.
 *
 * 이벤트 판매분이 원본 상품의 카테고리로 합쳐지는지, 삭제 상품이 빠지는지는 실제 SQL이 돌아야 드러난다.
 */
@EnabledIfDockerAvailable
@SpringBootTest(classes = EeumApplication.class)
@RequiredArgsConstructor
class OrderItemCategorySalesIntegrationTest extends IntegrationTestSupport {

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private Long storeId;
    private Long drinkCategoryId;
    private Long dessertCategoryId;

    private Account buyer;
    private Store store;
    private int orderSequence;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(Account.createOwner(
                "category-sales-owner@test.com", "encoded_pw", "카테고리 통계 사장", "010-5555-5555"));
        buyer = accountRepository.save(Account.createUser(
                "category-sales-buyer@test.com", "encoded_pw", "구매자", "카테고리구매자", "010-6666-6666"));

        store = storeRepository.save(Store.createForOwnerSignup(
                owner, "카테고리 통계 상점", "서울시 강남구", "02-5555-5555"));
        storeId = store.getStoreId();

        ProductCategory drink = productCategoryRepository.save(ProductCategory.create(store, "음료", 1));
        ProductCategory dessert = productCategoryRepository.save(ProductCategory.create(store, "디저트", 2));
        drinkCategoryId = drink.getProductCategoryId();
        dessertCategoryId = dessert.getProductCategoryId();

        Product americano = productRepository.save(Product.create(
                store, drink, "아메리카노", null, new BigDecimal("4000"), 100, ProductType.MENU));
        Product latte = productRepository.save(Product.create(
                store, drink, "라떼", null, new BigDecimal("5000"), 100, ProductType.MENU));
        Product cake = productRepository.save(Product.create(
                store, dessert, "케이크", null, new BigDecimal("7000"), 100, ProductType.MENU));
        Product deleted = productRepository.save(Product.create(
                store, dessert, "단종된 디저트", null, new BigDecimal("3000"), 100, ProductType.MENU));
        deleted.deactivate();
        productRepository.save(deleted);

        EventProduct americanoEvent = eventProductRepository.save(EventProduct.create(
                americano,
                new BigDecimal("3000"),
                50,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)));

        // 음료: 아메리카노 2개(8,000) + 이벤트 아메리카노 3개(9,000) + 라떼 1개(5,000) = 6개, 22,000
        saveCompletedOrder(productItem(americano, 2), eventItem(americanoEvent, 3));
        saveCompletedOrder(productItem(latte, 1));
        // 디저트: 케이크 2개(14,000). 삭제 상품 판매분은 빠진다
        saveCompletedOrder(productItem(cake, 2), productItem(deleted, 9));
        saveOrder(OrderStatus.CANCELLED, productItem(cake, 100));
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
    void 이벤트_판매분은_원본_카테고리에_합산되고_삭제된_상품은_제외된다() {
        List<CategorySalesStat> result = orderItemRepository.aggregateSalesByCategory(
                storeId, List.of(OrderStatus.COMPLETED), null, null);

        assertThat(result)
                .extracting(CategorySalesStat::categoryId, CategorySalesStat::soldQuantity)
                .containsExactly(
                        tuple(drinkCategoryId, 6L),
                        tuple(dessertCategoryId, 2L));
        assertThat(result.get(0).salesAmount()).isEqualByComparingTo("22000");
        assertThat(result.get(1).salesAmount()).isEqualByComparingTo("14000");
    }

    @Test
    void 지정한_주문_상태만_집계한다() {
        List<CategorySalesStat> result = orderItemRepository.aggregateSalesByCategory(
                storeId, List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED), null, null);

        assertThat(result)
                .extracting(CategorySalesStat::categoryId, CategorySalesStat::soldQuantity)
                .containsExactly(
                        tuple(dessertCategoryId, 102L),
                        tuple(drinkCategoryId, 6L));
    }

    @Test
    void 기간_밖의_주문은_집계에서_빠진다() {
        List<CategorySalesStat> result = orderItemRepository.aggregateSalesByCategory(
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
                "CATEGORY-SALES-TEST-" + (++orderSequence), OrderType.SALE, null, null);
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
