package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "store_table",
        indexes = {
                @Index(name = "idx_store_table_store_active", columnList = "store_id, active"),
                @Index(name = "idx_store_table_store_capacity", columnList = "store_id, capacity")
        }
)
public class StoreTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_table_id")
    private Long storeTableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 50)
    private String tableName;

    @Column(nullable = false)
    private boolean active = true;

    public static StoreTable create(Store store, int capacity, String tableName) {
        StoreTable table = new StoreTable();
        table.store = store;
        table.capacity = capacity;
        table.tableName = tableName;
        table.active = true;
        return table;
    }

    public void deactivate() {
        this.active = false;
    }
}
