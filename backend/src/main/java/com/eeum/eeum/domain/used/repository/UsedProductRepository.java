package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsedProductRepository extends JpaRepository<UsedProduct, Long> {

    //  Soft Delete 대상이므로 단건 조회는 항상 이 메서드 사용
    Optional<UsedProduct> findByUsedProductIdAndDeletedAtIsNull(Long usedProductId);
}
