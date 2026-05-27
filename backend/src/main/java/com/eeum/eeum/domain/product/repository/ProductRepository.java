package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByStore_StoreIdAndProductType(Long storeId, ProductType productType);
    List<Product> findByStore_StoreId(Long storeId);

    Optional<Product> findFirstByStore_StoreIdAndProductTypeOrderByCreatedAtAsc(
            Long storeId,
            ProductType productType
    );

    List<Product> findByStore_StoreIdAndStatusNot(Long storeId, ProductStatus productStatus);

    void deleteByStore_StoreId(Long storeId);
}