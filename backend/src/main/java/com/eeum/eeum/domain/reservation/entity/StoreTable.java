package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "store_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
