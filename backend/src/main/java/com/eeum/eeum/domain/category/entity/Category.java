package com.eeum.eeum.domain.category.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.category.enums.CategoryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "category",
        indexes = {
                @Index(name = "idx_category_type", columnList = "type"),
                @Index(name = "idx_category_parent_id", columnList = "parent_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    public static final int MAX_DEPTH = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private CategoryType type;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "depth", nullable = false)
    private int depth = 1;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public static Category createRoot(
            CategoryType type,
            String name,
            int displayOrder
    ) {
        Category category = new Category();
        category.type = type;
        category.parentId = null;
        category.name = name;
        category.displayOrder = displayOrder;
        category.depth = 1;
        category.isActive = true;
        return category;
    }

    public static Category createChild(
            CategoryType type,
            Long parentId,
            String name,
            int displayOrder,
            int depth
    ) {
        Category category = new Category();
        category.type = type;
        category.parentId = parentId;
        category.name = name;
        category.displayOrder = displayOrder;
        category.depth = depth;
        category.isActive = true;
        return category;
    }

    public void updateInfo(
            String name,
            int displayOrder
    ) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public boolean canHaveChild() {
        return depth < MAX_DEPTH;
    }
}
