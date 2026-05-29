package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cart_item")
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_product_id")
    private EventProduct eventProduct;

    @Column(name = "selected_option_item_ids", length = 1000)
    private String selectedOptionItemIds;

    @Column(name = "selected_options_text", columnDefinition = "TEXT")
    private String selectedOptionsText;

    @Column(name = "selected_options_hash", nullable = false, length = 64)
    private String selectedOptionsHash;

    @Column(name = "options_total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal optionsTotalPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * 상품 기본 가격 + 옵션 추가 금액이 반영된 1개 기준 최종 단가
     */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    public static CartItem createForProduct(
            Cart cart,
            Product product,
            String selectedOptionItemIds,
            String selectedOptionsText,
            BigDecimal optionsTotalPrice,
            String selectedOptionsHash,
            int quantity
    ) {
        CartItem item = new CartItem();
        item.cart = cart;
        item.product = product;
        item.eventProduct = null;
        item.selectedOptionItemIds = selectedOptionItemIds;
        item.selectedOptionsText = selectedOptionsText;
        item.optionsTotalPrice = optionsTotalPrice;
        item.selectedOptionsHash = selectedOptionsHash;
        item.quantity = quantity;
        item.unitPrice = product.getPrice().add(optionsTotalPrice);
        return item;
    }

    public static CartItem createForEventProduct(
            Cart cart,
            EventProduct eventProduct,
            int quantity
    ) {
        CartItem item = new CartItem();
        item.cart = cart;
        item.product = null;
        item.eventProduct = eventProduct;
        item.selectedOptionItemIds = null;
        item.selectedOptionsText = null;
        item.optionsTotalPrice = BigDecimal.ZERO;
        item.selectedOptionsHash = "none";
        item.quantity = quantity;
        item.unitPrice = eventProduct.getEventPrice();
        return item;
    }

    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalPrice() {
        return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

    public boolean isEventProduct() {
        return this.eventProduct != null;
    }
}