package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiPlanPaymentRepository extends JpaRepository<AiPlanPayment, Long> {

    Optional<AiPlanPayment> findByPortonePaymentId(String portonePaymentId);
}
