package com.eeum.eeum.domain.reservation.repository;

import com.eeum.eeum.domain.reservation.entity.StoreTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreTableRepository extends JpaRepository<StoreTable, Long> {

    // 테이블 구성 요약 — 활성 테이블을 로딩하지 않고 DB에서 capacity별 개수 집계
    // (idx_store_table_store_capacity 인덱스 활용)
    @Query("""
        SELECT t.capacity AS capacity, COUNT(t) AS count
        FROM StoreTable t
        WHERE t.store.storeId = :storeId
          AND t.active = true
        GROUP BY t.capacity
        ORDER BY t.capacity ASC
        """)

    List<StoreTable> findByStore_StoreIdOrderByCapacityAscStoreTableIdAsc(Long storeId);

    List<StoreTable> findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(Long storeId);

    List<StoreTable> findByStore_StoreIdAndActiveTrueAndCapacityGreaterThanEqualOrderByCapacityAscStoreTableIdAsc(
            Long storeId, int capacity);

    @Query("""
        SELECT t FROM StoreTable t
        WHERE t.store.storeId = :storeId
          AND t.active = true
          AND t.capacity >= :partySize
          AND t.storeTableId NOT IN :occupiedTableIds
        ORDER BY t.capacity ASC, t.storeTableId ASC
    """)
    List<StoreTable> findAvailableTablesExcludingOccupied(
            @Param("storeId") Long storeId,
            @Param("partySize") int partySize,
            @Param("occupiedTableIds") List<Long> occupiedTableIds
    );

    @Query("""
        SELECT st.capacity AS capacity,
               COUNT(st) AS count
        FROM StoreTable st
        WHERE st.store.storeId = :storeId
          AND st.active = true
        GROUP BY st.capacity
        ORDER BY st.capacity ASC
        """)
    List<CapacityCountProjection> countActiveTablesByCapacity(
            @Param("storeId") Long storeId
    );
}
