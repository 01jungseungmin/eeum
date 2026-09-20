package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanPaymentCancellationOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiPlanPaymentCancellationOperationRepository
        extends JpaRepository<AiPlanPaymentCancellationOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AiPlanPaymentCancellationOperation o where o.payment.portonePaymentId = :paymentId")
    Optional<AiPlanPaymentCancellationOperation> findByPaymentIdWithPessimisticLock(@Param("paymentId") String paymentId);
}
