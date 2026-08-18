package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsedProductImageRepository extends JpaRepository<UsedProductImage, Long> {

    List<UsedProductImage> findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(Long usedProductId);

    int countByUsedProduct_UsedProductId(Long usedProductId);
}
