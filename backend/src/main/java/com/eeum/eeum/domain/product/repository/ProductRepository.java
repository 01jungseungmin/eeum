package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByStore_StoreIdAndProductType(Long storeId, ProductType productType);
}