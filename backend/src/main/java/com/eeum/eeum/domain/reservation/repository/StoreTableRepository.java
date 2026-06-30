package com.eeum.eeum.domain.reservation.repository;

import com.eeum.eeum.domain.reservation.entity.StoreTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreTableRepository extends JpaRepository<StoreTable, Long> {

    List<StoreTable> findByStore_StoreIdOrderByCapacityAscStoreTableIdAsc(Long storeId);

    List<StoreTable> findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(Long storeId);

    List<StoreTable> findByStore_StoreIdAndActiveTrueAndCapacityGreaterThanEqualOrderByCapacityAscStoreTableIdAsc(
            Long storeId, int capacity);

    @Query("""
        SELECT t FROM StoreTable t
        WHERE t.store.storeId = :storeId
          AND t.active = true
          AND t.capacity >= :visitorCount
          AND t.storeTableId NOT IN :occupiedTableIds
        ORDER BY t.capacity ASC, t.storeTableId ASC
    """)
    List<StoreTable> findAvailableTablesExcludingOccupied(
            @Param("storeId") Long storeId,
            @Param("visitorCount") int visitorCount,
            @Param("occupiedTableIds") List<Long> occupiedTableIds
    );
}
