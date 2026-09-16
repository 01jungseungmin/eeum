package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.ProductOptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
    select poi
    from ProductOptionItem poi
    where poi.productOption.productOptionId in :optionIds
    order by poi.productOption.displayOrder asc, poi.displayOrder asc
""")
    List<ProductOptionItem> findByOptionIdsOrderByOptionDisplayOrderAndItemDisplayOrder(
            @Param("optionIds") List<Long> optionIds
    );
}