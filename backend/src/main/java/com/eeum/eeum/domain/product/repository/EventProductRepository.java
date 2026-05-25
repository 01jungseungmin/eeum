package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.enums.EventProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventProductRepository extends JpaRepository<EventProduct, Long> {

    List<EventProduct> findByProduct_Store_StoreIdOrderByCreatedAtDesc(Long storeId);

    List<EventProduct> findByProduct_Store_StoreIdAndStatusOrderByCreatedAtDesc(
            Long storeId,
            EventProductStatus status
    );

    Optional<EventProduct> findByEventProductIdAndStatus(
            Long eventProductId,
            EventProductStatus status
    );

    boolean existsByProduct_ProductIdAndStatus(
            Long productId,
            EventProductStatus status
    );
}