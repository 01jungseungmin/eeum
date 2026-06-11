package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;

import java.math.BigDecimal;

public interface PortOnePaymentClient {

    PortOnePaymentInfo getPayment(String paymentId);
    void cancelPayment(String paymentId, BigDecimal amount, String reason);
}