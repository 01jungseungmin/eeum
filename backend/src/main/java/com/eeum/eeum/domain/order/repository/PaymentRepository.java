package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

    Optional<Payment> findByOrder_OrderId(Long orderId);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    @Query("SELECT p.order.orderId FROM Payment p WHERE p.portonePaymentId = :portonePaymentId")
    Optional<Long> findOrderIdByPortonePaymentId(@Param("portonePaymentId") String portonePaymentId);

    Page<Payment> findByOrder_Account_AccountId(Long accountId, Pageable pageable);

    Optional<Payment> findByOrder_Account_AccountIdAndPaymentId(Long accountId, Long paymentId);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime threshold);


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
