package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentResponse;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOnePaymentClientImpl implements PortOnePaymentClient {

    private final PortOneProperties portOneProperties;

    @Override
    public PortOnePaymentInfo getPayment(String paymentId) {
        try {
            PortOnePaymentResponse response = RestClient.create(portOneProperties.baseUrl())
                    .get()
                    .uri("/payments/{paymentId}", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + portOneProperties.apiSecret())
                    .retrieve()
                    .body(PortOnePaymentResponse.class);

            if (response == null || response.getAmount() == null) {
                throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
            }

            return PortOnePaymentInfo.builder()
                    .paymentId(response.getId())
                    .status(response.getStatus())
                    .amount(response.getAmount().getTotal())
                    .pgProvider(response.getPgProvider())
                    .build();

        } catch (RestClientException e) {
            log.error("PortOne 결제 조회 실패: paymentId={}", paymentId, e);
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
    }
}