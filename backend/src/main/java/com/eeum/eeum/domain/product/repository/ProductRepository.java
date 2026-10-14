package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByStore_StoreIdAndProductType(Long storeId, ProductType productType);
    List<Product> findByStore_StoreId(Long storeId);

    Optional<Product> findFirstByStore_StoreIdAndProductTypeOrderByCreatedAtAsc(
            Long storeId,
            ProductType productType
    );

    long countByStore_StoreId(Long storeId);

    long countByStore_StoreIdAndStatus(Long storeId, ProductStatus productStatus);

    List<Product> findByStore_StoreIdAndProductCategory_IsActiveTrueAndStatusNot(
            Long storeId, ProductStatus productStatus);

    List<Product> findByStore_StoreIdAndProductCategory_ProductCategoryIdAndProductCategory_IsActiveTrueAndStatusNot(
            Long storeId,
            Long productCategoryId,
            ProductStatus productStatus
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> findByIdWithPessimisticLock(Long productId);

    // 조회수는 상품 상태/version과 독립적인 원자 카운터다. Product는 @DynamicUpdate로
    // 다른 필드 저장 시 이 카운터를 낡은 값으로 덮어쓰지 않는다.
    @Modifying
    @Query("update Product p set p.viewCount = p.viewCount + 1 where p.productId = :productId")
    int incrementViewCount(@Param("productId") Long productId);

    @Query("select p.viewCount from Product p where p.productId = :productId")
    Integer findViewCount(@Param("productId") Long productId);

    // 환불·취소 복원은 주문 차감과 경쟁할 수 있으므로 DB 현재 값에 원자적으로 더한다.
    // 버전도 올려 이전 스냅샷의 주문/상품 변경이 복원량을 덮어쓰지 못하게 한다.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p
               set p.stock = p.stock + :quantity,
                   p.status = case when p.status = :soldOut then :active else p.status end,
                   p.version = p.version + 1
             where p.productId = :productId and p.stock is not null
            """)
    int restoreStockAtomically(@Param("productId") Long productId,
                               @Param("quantity") int quantity,
                               @Param("soldOut") ProductStatus soldOut,
                               @Param("active") ProductStatus active);
}
