package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreImageRepository extends JpaRepository<StoreImage, Long> {
    List<StoreImage> findByStore_StoreIdOrderByDisplayOrderAsc(Long storeId);
    int countByStore_StoreId(Long storeId);
    void deleteByStore_StoreId(Long storeId);
    boolean existsByStore_StoreIdAndIsThumbnailTrue(Long storeId);
    Optional<StoreImage> findByStore_StoreIdAndIsThumbnailTrue(Long storeId);
}