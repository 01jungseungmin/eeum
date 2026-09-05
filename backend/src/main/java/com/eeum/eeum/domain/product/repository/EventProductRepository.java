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

    Optional<EventProduct> findFirstByProduct_Store_StoreIdOrderByCreatedAtDesc(Long storeId);

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



    // 특정 상품의 현재 진행 중인 이벤트 조회.
    // List + 최신순 정렬로 반환한다 — 동시 생성 등으로 ACTIVE 이벤트가 2건 이상 생겨도 단건 조회가
    // NonUniqueResultException(상품 상세 500)으로 터지지 않도록 방어한다.
    @Query("""
            SELECT ep
            FROM EventProduct ep
            JOIN FETCH ep.product p
            WHERE p.productId = :productId
              AND ep.status = :status
              AND ep.startAt <= :now
              AND ep.endAt > :now
            ORDER BY ep.createdAt DESC
            """)
    List<EventProduct> findActiveEventsByProductId(
            @Param("productId") Long productId,
            @Param("status") EventProductStatus status,
            @Param("now") LocalDateTime now
    );

    default Optional<EventProduct> findActiveEventByProductId(Long productId) {
        return findActiveEventsByProductId(
                productId,
                EventProductStatus.ACTIVE,
                LocalDateTime.now()
        ).stream().findFirst();
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
    Optional<EventProduct> findByIdWithPessimisticLock(@Param("eventProductId") Long eventProductId);

    // 만료 시각이 지난 ACTIVE 이벤트 상품을 일괄 ENDED 처리 (스케줄러 전용).
    // version도 함께 올린다 — 올리지 않으면 만료 전에 EventProduct를 읽어둔 요청이
    // 나중에 저장할 때 낙관적 락에 걸리지 않아 ENDED가 ACTIVE로 되돌아간다.
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE EventProduct ep
        SET ep.status = :ended,
            ep.version = ep.version + 1
        WHERE ep.status = :active AND ep.endAt <= :now
        """)
    int bulkEndExpiredEvents(
            @Param("active") EventProductStatus active,
            @Param("ended") EventProductStatus ended,
            @Param("now") LocalDateTime now
    );

}