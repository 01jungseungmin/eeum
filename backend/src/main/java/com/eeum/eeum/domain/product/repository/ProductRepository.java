package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> findByIdWithPessimisticLock(Long productId);
}