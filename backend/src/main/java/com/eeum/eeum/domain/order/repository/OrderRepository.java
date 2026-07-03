package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.CustomerOrderStatProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;



public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByAccount_AccountIdOrderByCreatedAtDesc(
            Long accountId,
            Pageable pageable
    );

    Optional<Order> findByOrderIdAndAccount_AccountId(Long orderId, Long accountId);

    Page<Order> findByStore_StoreId(Long storeId, Pageable pageable);

    Page<Order> findByStore_StoreIdAndStatus(Long storeId, OrderStatus status, Pageable pageable);

    Page<Order> findByStore_StoreIdAndAccount_AccountIdOrderByCreatedAtDesc(
            Long storeId, Long accountId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    long countByStore_StoreIdAndCreatedAtBetween(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );

    long countByStore_StoreIdAndStatus(
            Long storeId,
            OrderStatus status
    );

    List<Order> findByStore_StoreIdAndStatusAndCreatedAtBetween(
            Long storeId,
            OrderStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

    List<Order> findByStore_StoreIdAndStatus(
            Long storeId,
            OrderStatus status
    );

    boolean existsByOrderNumber(String orderNumber);

    boolean existsByStore_StoreIdAndStatusIn(
            Long storeId,
            Collection<OrderStatus> statuses
    );

    @Query("""
    SELECT COALESCE(SUM(o.totalPrice), 0)
    FROM Order o
    WHERE o.store.storeId = :storeId
      AND o.createdAt BETWEEN :from AND :to
      AND o.status = :status
""")
    BigDecimal sumTotalPriceByStoreAndCreatedAtBetween(
            @Param("storeId") Long storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") OrderStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.orderId = :orderId
    """)
    Optional<Order> findByIdWithPessimisticLock(
            @Param("orderId") Long orderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.orderNumber = :orderNumber
    """)
    Optional<Order> findByOrderNumberWithPessimisticLock(
            @Param("orderNumber") String orderNumber
    );

    // 사장 통합 고객 목록 — 고객별 완료 주문 통계 배치 조회 (IN절 1번으로 N+1 방지)
    // recentOrderCount: :sixMonthsAgo 이후 완료 주문 수 (REGULAR 판정용)
    @Query("""
        SELECT o.account.accountId AS accountId,
               COUNT(o.orderId)    AS orderCount,
               SUM(o.totalPrice)   AS totalAmount,
               MAX(o.completedAt)  AS lastOrderedAt,
               SUM(CASE WHEN o.completedAt >= :sixMonthsAgo THEN 1 ELSE 0 END) AS recentOrderCount
        FROM Order o
        WHERE o.store.storeId         = :storeId
          AND o.account.accountId     IN :accountIds
          AND o.status                = :status
        GROUP BY o.account.accountId
    """)
    List<CustomerOrderStatProjection> findOrderStatsByStoreAndAccounts(
            @Param("storeId") Long storeId,
            @Param("accountIds") List<Long> accountIds,
            @Param("status") OrderStatus status,
            @Param("sixMonthsAgo") LocalDateTime sixMonthsAgo
    );

    // 사장 통합 고객 목록 — 완료 주문이 있는 고객 accountId 목록 (합집합 구성용)
    @Query("""
        SELECT DISTINCT o.account.accountId
        FROM Order o
        WHERE o.store.storeId = :storeId
          AND o.status = :status
    """)
    List<Long> findOrderAccountIdsByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") OrderStatus status);

    // 사장 요약 통계 — 상점 전체 완료 주문 누적 매출
    @Query("""
        SELECT COALESCE(SUM(o.totalPrice), 0)
        FROM Order o
        WHERE o.store.storeId = :storeId
          AND o.status = :status
    """)
    BigDecimal sumOrderAmountByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") OrderStatus status);

    @Query("""
        SELECT o
        FROM Order o
        JOIN Payment p ON p.order = o
        WHERE o.status = :orderStatus
          AND p.status = :paymentStatus
          AND o.createdAt <= :threshold
    """)
    List<Order> findPaymentPendingOrdersBefore(
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("threshold") LocalDateTime threshold
    );

}