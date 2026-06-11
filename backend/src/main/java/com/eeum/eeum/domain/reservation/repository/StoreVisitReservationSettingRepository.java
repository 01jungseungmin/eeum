package com.eeum.eeum.domain.reservation.repository;

import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreVisitReservationSettingRepository extends JpaRepository<StoreVisitReservationSetting,Long> {
    Optional<StoreVisitReservationSetting> findByStore_StoreId(Long storeId);

    boolean existsByStore_StoreId(Long storeId);

}
