package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByStore_StoreIdOrderByDisplayOrderAsc(Long storeId);

    List<ProductCategory> findByStore_StoreIdAndIsActiveTrueOrderByDisplayOrderAsc(
            Long storeId
    );

    Optional<ProductCategory> findByProductCategoryIdAndStore_StoreId(
            Long productCategoryId,
            Long storeId
    );

    boolean existsByStore_StoreIdAndName(Long storeId, String name);

    boolean existsByStore_StoreIdAndNameAndProductCategoryIdNot(
            Long storeId,
            String name,
            Long productCategoryId
    );

    void deleteByStore_StoreId(Long storeId);

    @Query("""
        select count(p)
        from Product p
        where p.productCategory.productCategoryId = :categoryId
    """)
    int countProductsByCategoryId(@Param("categoryId") Long categoryId);

    boolean existsByStore_StoreId(Long storeId);

    Optional<ProductCategory> findByStore_StoreIdAndName(Long storeId, String name);
}