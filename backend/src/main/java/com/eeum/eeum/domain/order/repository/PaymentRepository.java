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

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_OrderId(Long orderId);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    @Query("SELECT p.order.orderId FROM Payment p WHERE p.portonePaymentId = :portonePaymentId")
    Optional<Long> findOrderIdByPortonePaymentId(@Param("portonePaymentId") String portonePaymentId);

    Page<Payment> findByOrder_Account_AccountId(Long accountId, Pageable pageable);

    Optional<Payment> findByOrder_Account_AccountIdAndPaymentId(Long accountId, Long paymentId);


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

    // 관리자 대시보드 실시간 활동 — 최근 결제 완료 건. 상점명까지 한 번에 읽어 N+1을 피한다
    @Query("""
        SELECT new com.eeum.eeum.domain.order.repository.PaymentActivity(p.paymentId, s.name, p.amount, p.paidAt)
        FROM Payment p
        JOIN p.order o
        JOIN o.store s
        WHERE p.paidAt IS NOT NULL
        ORDER BY p.paidAt DESC
        """)
    List<PaymentActivity> findRecentPaymentActivities(Pageable pageable);
}
