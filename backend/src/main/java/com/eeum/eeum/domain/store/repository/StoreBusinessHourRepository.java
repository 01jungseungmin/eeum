package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreBusinessHourRepository extends JpaRepository<StoreBusinessHour, Long> {

    List<StoreBusinessHour> findByStore_StoreIdOrderByDayOfWeekAsc(Long storeId);

    Optional<StoreBusinessHour> findByStore_StoreIdAndDayOfWeek(
            Long storeId,
            StoreDayOfWeek dayOfWeek
    );

    void deleteByStore_StoreId(Long storeId);

    long countByStore_StoreId(Long storeId);

    List<StoreBusinessHour> findByStore_StoreId(Long storeId);
}