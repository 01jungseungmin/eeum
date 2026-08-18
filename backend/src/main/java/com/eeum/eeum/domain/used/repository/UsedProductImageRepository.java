package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsedProductImageRepository extends JpaRepository<UsedProductImage, Long> {

    List<UsedProductImage> findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(Long usedProductId);

    int countByUsedProduct_UsedProductId(Long usedProductId);

    // 여러 게시글의 대표 사진을 한 번에 읽기
    List<UsedProductImage> findByUsedProduct_UsedProductIdInAndIsThumbnailTrue(List<Long> usedProductIds);
}
