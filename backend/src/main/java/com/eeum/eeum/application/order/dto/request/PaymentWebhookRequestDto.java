package com.eeum.eeum.application.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "PortOne Webhook 요청")
public class PaymentWebhookRequestDto {

    private String type;
    private String timestamp;
    private Data data;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String paymentId;
    }

    /** 2024-01-01 형식과의 일시적 호환용. 최신 Standard Webhooks는 data.paymentId를 쓴다. */
    @Schema(description = "PortOne paymentId", example = "pay_20260528130000_ABCD1234")
    private String paymentId;

    public String resolvedPaymentId() {
        return data != null && data.paymentId != null ? data.paymentId : paymentId;
    }
}
