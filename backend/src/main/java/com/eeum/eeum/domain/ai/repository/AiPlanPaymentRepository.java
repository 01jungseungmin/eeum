package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiPlanPaymentRepository extends JpaRepository<AiPlanPayment, Long> {

    Optional<AiPlanPayment> findByPortonePaymentId(String portonePaymentId);

    Optional<AiPlanPayment> findFirstByStore_StoreIdAndPlanTypeAndStatusOrderByCreatedAtDesc(
            Long storeId, com.eeum.eeum.domain.ai.enums.AiPlanType planType, AiPlanPaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AiPlanPayment p where p.portonePaymentId = :paymentId")
    Optional<AiPlanPayment> findByPortonePaymentIdWithPessimisticLock(@Param("paymentId") String paymentId);

    // 결제 대기 만료 스케줄러 — 오래 PENDING 상태로 남은 결제 정리
    List<AiPlanPayment> findByStatusAndCreatedAtBeforeOrderByCreatedAtAscAiPlanPaymentIdAsc(
            AiPlanPaymentStatus status, LocalDateTime threshold, Pageable pageable);
}
