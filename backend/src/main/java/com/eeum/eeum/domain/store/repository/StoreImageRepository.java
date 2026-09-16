package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.store.entity.StoreImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreImageRepository extends JpaRepository<StoreImage, Long> {
    List<StoreImage> findByStore_StoreIdOrderByDisplayOrderAsc(Long storeId);
    int countByStore_StoreId(Long storeId);
    boolean existsByStore_StoreIdAndIsThumbnailTrue(Long storeId);
    Optional<StoreImage> findByStore_StoreIdAndIsThumbnailTrue(Long storeId);

    // 상점 찜 목록 N+1 방지 — 여러 상점의 썸네일을 IN절 한 번으로 조회
    List<StoreImage> findByStore_StoreIdInAndIsThumbnailTrue(List<Long> storeIds);
}