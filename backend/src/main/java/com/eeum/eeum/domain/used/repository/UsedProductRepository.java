package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsedProductRepository
        extends JpaRepository<UsedProduct, Long>, UsedProductRepositoryCustom {

    //  Soft Delete 대상이므로 단건 조회는 항상 이 메서드 사용
    Optional<UsedProduct> findByUsedProductIdAndDeletedAtIsNull(Long usedProductId);

    // 찜 카운트 +1 — DB 원자 UPDATE. 읽어서 +1 후 저장하면 동시 찜이 서로의 증가분을 덮어쓴다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UsedProduct p SET p.favoriteCount = p.favoriteCount + 1 WHERE p.usedProductId = :usedProductId")
    int incrementFavoriteCount(@Param("usedProductId") Long usedProductId);

    // 찜 카운트 -1 — favoriteCount > 0 가드로 음수로 내려가지 않게 한다.
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UsedProduct p
        SET p.favoriteCount = p.favoriteCount - 1
        WHERE p.usedProductId = :usedProductId
          AND p.favoriteCount > 0
        """)
    int decrementFavoriteCount(@Param("usedProductId") Long usedProductId);
}
