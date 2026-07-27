package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class MockPortOnePaymentClient implements PortOnePaymentClient {

    private final PaymentRepository paymentRepository;
    private final AiPlanPaymentRepository aiPlanPaymentRepository;

    @Value("${portone.mock.status:PAID}")
    private String mockStatus;

    @Value("${portone.mock.amount:19000}")
    private BigDecimal mockAmount;

    @Override
    public PortOnePaymentInfo getPayment(String paymentId) {
        // 실제 결제 요청 금액을 그대로 돌려줘야 금액 검증을 통과한다.
        // 주문 결제(Payment) → AI 플랜 결제(AiPlanPayment) 순으로 조회, 둘 다 없으면 설정값 fallback
        BigDecimal amount = paymentRepository.findByPortonePaymentId(paymentId)
                .map(payment -> payment.getAmount())
                .or(() -> aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                        .map(planPayment -> planPayment.getAmount()))
                .orElse(mockAmount);

        log.info("[LOCAL MOCK] PortOne 결제 조회 Mock 처리: paymentId={}, status={}, amount={}",
                paymentId, mockStatus, amount);

        return PortOnePaymentInfo.builder()
                .paymentId(paymentId)
                .status(mockStatus)
                .amount(amount)
                .pgProvider("LOCAL_MOCK")
                .build();
    }

    @Override
    public void cancelPayment(String paymentId, BigDecimal amount, String reason) {
        log.info("[LOCAL MOCK] PortOne 결제 취소 Mock 처리: paymentId={}, amount={}, reason={}",
                paymentId, amount, reason);
    }
}
