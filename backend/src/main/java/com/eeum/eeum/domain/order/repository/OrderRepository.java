package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByAccount_AccountIdOrderByCreatedAtDesc(
            Long accountId,
            Pageable pageable
    );

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    boolean existsByStore_StoreIdAndStatusIn(
            Long storeId,
            Collection<OrderStatus> statuses
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