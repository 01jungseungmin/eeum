package com.eeum.eeum.domain.product.repository;

import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.enums.EventProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventProductRepository extends JpaRepository<EventProduct, Long> {

    List<EventProduct> findByProduct_Store_StoreIdOrderByCreatedAtDesc(Long storeId);

    boolean existsByProduct_ProductIdAndStatusAndEndAtAfter(Long productId, EventProductStatus eventProductStatus, LocalDateTime now);



    List<EventProduct> findByProduct_Store_StoreIdAndStatusOrderByCreatedAtDesc(
            Long storeId,
            EventProductStatus status
    );

    Optional<EventProduct> findByEventProductIdAndStatus(
            Long eventProductId,
            EventProductStatus status
    );

    List<EventProduct> findByProduct_Store_StoreId(Long storeId);

    void deleteByProduct_ProductId(Long productId);


    // 특정 상품의 현재 진행 중인 이벤트 조회
    @Query("""
            SELECT ep
            FROM EventProduct ep
            JOIN FETCH ep.product p
            WHERE p.productId = :productId
              AND ep.status = :status
              AND ep.startAt <= :now
              AND ep.endAt > :now
            """)
    Optional<EventProduct> findActiveEventByProductId(
            @Param("productId") Long productId,
            @Param("status") EventProductStatus status,
            @Param("now") LocalDateTime now
    );

    default Optional<EventProduct> findActiveEventByProductId(Long productId) {
        return findActiveEventByProductId(
                productId,
                EventProductStatus.ACTIVE,
                LocalDateTime.now()
        );
    }

    // 특정 상점의 현재 진행 중인 이벤트 목록 조회
    @Query("""
            SELECT ep
            FROM EventProduct ep
            JOIN FETCH ep.product p
            JOIN FETCH p.store s
            WHERE s.storeId = :storeId
              AND ep.status = :status
              AND ep.startAt <= :now
              AND ep.endAt > :now
            ORDER BY ep.createdAt DESC
            """)
    List<EventProduct> findActiveEventsByStoreId(
            @Param("storeId") Long storeId,
            @Param("status") EventProductStatus status,
            @Param("now") LocalDateTime now
    );

    default List<EventProduct> findActiveEventsByStoreId(Long storeId) {
        return findActiveEventsByStoreId(
                storeId,
                EventProductStatus.ACTIVE,
                LocalDateTime.now()
        );
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ep from EventProduct ep where ep.eventProductId = :eventProductId")
    Optional<EventProduct> findByIdWithPessimisticLock(Long eventProductId);

    // 만료 시각이 지난 ACTIVE 이벤트 상품을 일괄 ENDED 처리 (스케줄러 전용)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EventProduct ep SET ep.status = :ended WHERE ep.status = :active AND ep.endAt <= :now")
    int bulkEndExpiredEvents(
            @Param("active") EventProductStatus active,
            @Param("ended") EventProductStatus ended,
            @Param("now") LocalDateTime now
    );

}