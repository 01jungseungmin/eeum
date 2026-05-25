package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "product_category_id")
    // private ProductCategory productCategory;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock")
    private Integer stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Product create(Store store, String name, String description,
                                 BigDecimal price, Integer stock, ProductType productType) {
        Product product = new Product();
        product.store = store;
        product.name = name;
        product.description = description;
        product.price = price;
        product.stock = stock;
        product.productType = productType;
        product.viewCount = 0;
        product.status = ProductStatus.ACTIVE;
        return product;
    }

    public void update(String name, String description,
                       BigDecimal price, Integer stock, ProductType productType) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.productType = productType;
    }

    public void soldOut() { this.status = ProductStatus.SOLD_OUT; }
    public void activate() { this.status = ProductStatus.ACTIVE; }
    public void deactivate() { this.status = ProductStatus.INACTIVE; }
}