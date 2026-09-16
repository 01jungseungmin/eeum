package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiPlanPaymentRepository extends JpaRepository<AiPlanPayment, Long> {

    Optional<AiPlanPayment> findByPortonePaymentId(String portonePaymentId);

    // 결제 대기 만료 스케줄러 — 오래 PENDING 상태로 남은 결제 정리
    List<AiPlanPayment> findByStatusAndCreatedAtBefore(AiPlanPaymentStatus status, LocalDateTime threshold);
}
