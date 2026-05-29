package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_OrderId(Long orderId);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByPortonePaymentIdAndStatus(
            String portonePaymentId,
            PaymentStatus status
    );
}