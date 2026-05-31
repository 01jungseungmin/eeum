package com.eeum.eeum.application.order.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class PortOnePaymentResponse {

    private String id;
    private String status;
    private Amount amount;
    private String pgProvider;

    @Getter
    @NoArgsConstructor
    public static class Amount {
        private BigDecimal total;
    }
}