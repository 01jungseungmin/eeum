package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_option_item")
public class ProductOptionItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_option_item_id")
    private Long productOptionItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOption productOption;

    @Column(name = "item_name", nullable = false, length = 50)
    private String itemName;

    @Column(name = "additional_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalPrice;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    public static ProductOptionItem create(ProductOption option, String itemName,
            BigDecimal additionalPrice, boolean isDefault, int displayOrder) {
        ProductOptionItem item = new ProductOptionItem();
        item.productOption = option;
        item.itemName = itemName;
        item.additionalPrice = additionalPrice;
        item.isDefault = isDefault;
        item.displayOrder = displayOrder;
        item.isAvailable = true;
        return item;
    }

    public void toggleAvailability() {
        this.isAvailable = !this.isAvailable;
    }
}