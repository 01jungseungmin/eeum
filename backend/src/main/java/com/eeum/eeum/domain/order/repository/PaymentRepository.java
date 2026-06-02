package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_OrderId(Long orderId);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByPortonePaymentIdAndStatus(
            String portonePaymentId,
            PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Payment p
        WHERE p.paymentId = :paymentId
    """)
    Optional<Payment> findByPaymentIdWithPessimisticLock(
            @Param("paymentId") Long paymentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Payment p
        WHERE p.order.orderId = :orderId
    """)
    Optional<Payment> findByOrderIdWithPessimisticLock(
            @Param("orderId") Long orderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Payment p
        WHERE p.portonePaymentId = :portonePaymentId
    """)
    Optional<Payment> findByPortonePaymentIdWithPessimisticLock(
            @Param("portonePaymentId") String portonePaymentId
    );
}