package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_category")
public class ProductCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_category_id")
    private Long productCategoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public static ProductCategory create(Store store, String name, int displayOrder) {
        ProductCategory pc = new ProductCategory();
        pc.store = store;
        pc.name = name;
        pc.displayOrder = displayOrder;
        pc.isActive = true;
        return pc;
    }

    public void update(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public void deactivate() { this.isActive = false; }
    public void activate() { this.isActive = true; }
}