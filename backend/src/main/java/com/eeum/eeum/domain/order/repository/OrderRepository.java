package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<Order> findByStatusAndCreatedAtBefore(
            OrderStatus status,
            LocalDateTime threshold
    );

    boolean existsByStore_StoreIdAndStatusIn(
            Long storeId,
            Collection<OrderStatus> statuses
    );
}