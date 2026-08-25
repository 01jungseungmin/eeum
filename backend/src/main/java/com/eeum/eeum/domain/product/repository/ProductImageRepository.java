package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProduct_ProductIdOrderByDisplayOrderAsc(Long productId);
    int countByProduct_ProductId(Long productId);
    boolean existsByProduct_ProductIdAndIsThumbnailTrue(Long productId);

    Optional<ProductImage> findFirstByProduct_ProductIdAndIsThumbnailTrueOrderByDisplayOrderAsc(Long productId);
}