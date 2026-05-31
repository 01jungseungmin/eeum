package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.ProductOptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductOptionItemRepository extends JpaRepository<ProductOptionItem, Long> {

    List<ProductOptionItem> findByProductOption_ProductOptionIdOrderByDisplayOrderAsc(
            Long optionId
    );

    Optional<ProductOptionItem> findByProductOptionItemIdAndProductOption_Product_ProductId(
            Long productOptionItemId,
            Long productId
    );

    List<ProductOptionItem> findByProductOptionItemIdInAndProductOption_Product_ProductId(
            List<Long> productOptionItemId,
            Long productId
    );

    void deleteByProductOption_ProductOptionId(Long optionId);
}