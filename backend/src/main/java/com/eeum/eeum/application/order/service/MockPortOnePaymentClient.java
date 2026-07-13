package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Profile("local")
public class MockPortOnePaymentClient implements PortOnePaymentClient {

    @Value("${portone.mock.status:PAID}")
    private String mockStatus;

    @Value("${portone.mock.amount:19000}")
    private BigDecimal mockAmount;

    @Override
    public PortOnePaymentInfo getPayment(String paymentId) {
        log.info("[LOCAL MOCK] PortOne 결제 조회 Mock 처리: paymentId={}, status={}, amount={}",
                paymentId, mockStatus, mockAmount);

        return PortOnePaymentInfo.builder()
                .paymentId(paymentId)
                .status(mockStatus)
                .amount(mockAmount)
                .pgProvider("LOCAL_MOCK")
                .build();
    }

    @Override
    public void cancelPayment(String paymentId, BigDecimal amount, String reason) {
        log.info("[LOCAL MOCK] PortOne 결제 취소 Mock 처리: paymentId={}, amount={}, reason={}",
                paymentId, amount, reason);
    }
}