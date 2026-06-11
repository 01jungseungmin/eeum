package com.eeum.eeum.application.order.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PortOnePaymentInfo {

    private String paymentId;

    private String status;

    private BigDecimal amount;

    private String pgProvider;
}