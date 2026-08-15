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
        },
        // 같은 부모 아래 같은 이름을 DB에서 유일하게 보장한다.
        // parent_id는 루트에서 NULL이고 MySQL UNIQUE는 NULL을 서로 다른 값으로 취급하므로
        // 루트 범위가 뚫린다. 그래서 NULL을 0으로 치환한 parent_scope를 별도로 두고 그 컬럼을 제약에 쓴다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_type_parent_scope_name",
                columnNames = {"type", "parent_scope", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    public static final int MAX_DEPTH = 3;

    /** 루트 카테고리의 parent_scope 값 — parent_id NULL을 대신한다. */
    private static final long ROOT_SCOPE = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private CategoryType type;

    @Column(name = "parent_id")
    private Long parentId;

    // parentId의 NULL 안전 사본 — 유니크 제약 전용이며 조회는 계속 parentId를 쓴다.
    @Column(name = "parent_scope", nullable = false,
            columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long parentScope = ROOT_SCOPE;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "depth", nullable = false)
    private int depth = 1;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // 이름/순서/활성 상태를 서로 다른 관리자가 동시에 바꿀 때의 lost update를 막는다.
    // Category는 @DynamicUpdate가 없어 Hibernate가 전체 컬럼을 UPDATE하므로,
    // 버전이 없으면 stale 스냅샷이 다른 관리자의 변경을 통째로 덮어쓴다.
    @Version
    @Column(name = "version", nullable = false,
            columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    public static Category createRoot(
            CategoryType type,
            String name,
            int displayOrder
    ) {
        Category category = new Category();
        category.type = type;
        category.parentId = null;
        category.parentScope = ROOT_SCOPE;
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
        category.parentScope = parentId;
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
