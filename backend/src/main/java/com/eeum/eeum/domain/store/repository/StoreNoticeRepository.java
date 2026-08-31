package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreNoticeRepository extends JpaRepository<StoreNotice, Long> {
    List<StoreNotice> findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(Long storeId);
}