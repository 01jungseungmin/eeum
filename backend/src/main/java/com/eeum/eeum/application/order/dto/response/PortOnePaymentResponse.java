package com.eeum.eeum.application.order.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
public class PortOnePaymentResponse {

    private String id;
    private String status;
    private Amount amount;
    private Channel channel;
    private List<Cancellation> cancellations;

    @Getter
    @NoArgsConstructor
    public static class Channel {
        private String pgProvider;
    }

    public String getPgProvider() {
        return channel == null ? null : channel.getPgProvider();
    }

    public BigDecimal getCancelledAmount() {
        if (cancellations == null) {
            return BigDecimal.ZERO;
        }
        return cancellations.stream()
                .filter(cancellation -> "SUCCEEDED".equalsIgnoreCase(cancellation.status))
                .map(Cancellation::resolveAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Getter
    @NoArgsConstructor
    public static class Cancellation {
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal cancelledAmount;

        private BigDecimal resolveAmount() {
            return totalAmount != null ? totalAmount : cancelledAmount;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class Amount {
        private BigDecimal total;
    }
}
