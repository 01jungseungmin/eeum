package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_option")
public class ProductOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_option_id")
    private Long productOptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(name = "selection_type", nullable = false, length = 20)
    private String selectionType; // SINGLE, MULTIPLE

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public static ProductOption create(Product product, String groupName,
            String selectionType, boolean isRequired, int displayOrder) {
        ProductOption option = new ProductOption();
        option.product = product;
        option.groupName = groupName;
        option.selectionType = selectionType;
        option.isRequired = isRequired;
        option.displayOrder = displayOrder;
        return option;
    }
}