package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanPaymentCancellationOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;

public interface AiPlanPaymentCancellationOperationRepository
        extends JpaRepository<AiPlanPaymentCancellationOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AiPlanPaymentCancellationOperation o where o.payment.portonePaymentId = :paymentId")
    Optional<AiPlanPaymentCancellationOperation> findByPaymentIdWithPessimisticLock(@Param("paymentId") String paymentId);

    @Query("""
        select o.payment.portonePaymentId
        from AiPlanPaymentCancellationOperation o
        where o.status = :status
          and o.modifiedAt < :threshold
        order by o.modifiedAt asc
    """)
    List<String> findPaymentIdsByStatusModifiedBefore(
            @Param("status") AiPlanPaymentCancellationStatus status,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable);
}
