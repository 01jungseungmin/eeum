package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsedProductRepository
        extends JpaRepository<UsedProduct, Long>, UsedProductRepositoryCustom {

    //  Soft Delete 대상이므로 단건 조회는 항상 이 메서드 사용
    Optional<UsedProduct> findByUsedProductIdAndDeletedAtIsNull(Long usedProductId);

    // 관리자 조치용 비관적 쓰기 잠금 — 조치와 작성자의 수정·삭제가 동시에 들어오는 경쟁을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UsedProduct p WHERE p.usedProductId = :usedProductId")
    Optional<UsedProduct> findByUsedProductIdForUpdate(@Param("usedProductId") Long usedProductId);

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
