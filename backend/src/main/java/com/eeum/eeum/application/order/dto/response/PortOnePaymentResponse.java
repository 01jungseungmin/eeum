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
    private Channel channel;

    @Getter
    @NoArgsConstructor
    public static class Channel {
        private String pgProvider;
    }

    public String getPgProvider() {
        return channel == null ? null : channel.getPgProvider();
    }

    @Getter
    @NoArgsConstructor
    public static class Amount {
        private BigDecimal total;
    }
}
