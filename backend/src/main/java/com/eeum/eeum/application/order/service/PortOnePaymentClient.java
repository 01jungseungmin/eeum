package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;

public interface PortOnePaymentClient {

    PortOnePaymentInfo getPayment(String paymentId);
}