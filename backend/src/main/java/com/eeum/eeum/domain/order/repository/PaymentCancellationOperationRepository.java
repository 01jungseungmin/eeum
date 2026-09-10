package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCancellationOperationRepository
        extends JpaRepository<PaymentCancellationOperation, Long> {

    Optional<PaymentCancellationOperation> findByOrder_OrderId(Long orderId);

    // 단계 전이는 항상 현재 행을 다시 읽어서 한다 — 준비 단계와 반영 단계가
    // 서로 다른 트랜잭션이라, 그 사이에 다른 경로가 같은 작업을 진전시켰을 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from PaymentCancellationOperation o
            where o.order.orderId = :orderId
            """)
    Optional<PaymentCancellationOperation> findByOrderIdWithPessimisticLock(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentCancellationOperation o WHERE o.paymentCancellationOperationId = :operationId")
    Optional<PaymentCancellationOperation> findByIdWithPessimisticLock(@Param("operationId") Long operationId);
}
