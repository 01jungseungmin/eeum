package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id", nullable = false)
    private ProductCategory productCategory;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
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

    public static Product create(
            Store store,
            ProductCategory productCategory,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            ProductType productType
    ) {
        Product product = new Product();
        product.store = store;
        product.productCategory = productCategory;
        product.name = name;
        product.description = description;
        product.price = price;
        product.stock = stock;
        product.productType = productType;
        product.status = ProductStatus.ACTIVE;
        product.viewCount = 0;
        return product;
    }

    public void update(
            ProductCategory productCategory,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            ProductType productType
    ) {
        this.productCategory = productCategory;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.productType = productType;
    }

    public void update_info(
            ProductCategory productCategory,
            String name,
            String description,
            BigDecimal price,
            ProductType productType
    ) {
        this.productCategory = productCategory;
        this.name = name;
        this.description = description;
        this.price = price;
        this.productType = productType;
    }

    public void inactive() {
        this.status = ProductStatus.INACTIVE;
    }

    public void updateStock(Integer stock) {
        this.stock = stock;
    }

    public void soldOut() { this.status = ProductStatus.SOLD_OUT; }
    public void activate() { this.status = ProductStatus.ACTIVE; }
    public void deactivate() { this.status = ProductStatus.INACTIVE; }

    public void increaseViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 0;
        }
        this.viewCount++;
    }

    public boolean hasUnlimitedStock() {
        return this.stock == null;
    }

    public void decreaseStock(int quantity) {
        if (this.stock == null) {
            return;
        }
        if (this.stock < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    public void increaseStock(int quantity) {
        if (this.stock == null) {
            return;
        }
        this.stock += quantity;
        if (this.stock > 0 && this.status == ProductStatus.SOLD_OUT) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    public void restoreStock(int quantity) {
        if (this.stock == null) {
            return;
        }

        this.stock += quantity;

        if (this.status == ProductStatus.SOLD_OUT && this.stock > 0) {
            this.status = ProductStatus.ACTIVE;
        }
    }
}