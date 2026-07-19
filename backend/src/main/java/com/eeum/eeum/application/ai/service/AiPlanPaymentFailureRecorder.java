package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 금액 불일치 등으로 FAILED 처리해야 하는데, 호출부가 같은 트랜잭션에서 예외를 던져 롤백되는 경우
 * FAILED 마킹이 함께 사라지는 것을 방지하기 위해 REQUIRES_NEW로 분리 커밋한다.
 */
@Service
@RequiredArgsConstructor
public class AiPlanPaymentFailureRecorder {

    private final AiPlanPaymentRepository aiPlanPaymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String paymentId) {
        aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                .ifPresent(AiPlanPayment::markFailed);
    }
}
