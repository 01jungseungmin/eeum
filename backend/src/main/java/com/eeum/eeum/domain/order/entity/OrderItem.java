package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.domain.product.enums.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "event_product_id")
    private Long eventProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "selected_option_item_ids", length = 1000)
    private String selectedOptionItemIds;

    @Column(name = "selected_options_text", columnDefinition = "TEXT")
    private String selectedOptionsText;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    //상품 기본 가격
    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    //옵션 추가 금액 합계
    @Column(name = "options_total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal optionsTotalPrice;

    //1개 기준 최종 단가
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    //unitPrice * quantity
    @Column(name = "line_total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotalPrice;

    public static OrderItem createFromCartItem(
            Order order,
            CartItem cartItem,
            String thumbnailUrl
    ) {
        OrderItem item = new OrderItem();
        item.order = order;

        if (cartItem.getProduct() != null) {
            item.productId = cartItem.getProduct().getProductId();
            item.eventProductId = null;
            item.productType = cartItem.getProduct().getProductType();
            item.productName = cartItem.getProduct().getName();
            item.basePrice = cartItem.getProduct().getPrice();
        } else {
            item.productId = null;
            item.eventProductId = cartItem.getEventProduct().getEventProductId();
            item.productType = cartItem.getEventProduct().getProduct().getProductType();
            item.productName = cartItem.getEventProduct().getProduct().getName();
            item.basePrice = cartItem.getEventProduct().getProduct().getPrice();
        }

        item.thumbnailUrl = thumbnailUrl;
        item.selectedOptionItemIds = cartItem.getSelectedOptionItemIds();
        item.selectedOptionsText = cartItem.getSelectedOptionsText();
        item.quantity = cartItem.getQuantity();
        item.optionsTotalPrice = cartItem.getOptionsTotalPrice();
        item.unitPrice = cartItem.getUnitPrice();
        item.lineTotalPrice = cartItem.getTotalPrice();

        return item;
    }
}