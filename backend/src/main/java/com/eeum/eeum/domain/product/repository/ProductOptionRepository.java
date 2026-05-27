package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProduct_ProductIdOrderByDisplayOrderAsc(Long productId);

    Optional<ProductOption> findByProductOptionIdAndProduct_ProductId(
            Long productOptionId,
            Long productId
    );

    void deleteByProduct_ProductId(Long productId);
}