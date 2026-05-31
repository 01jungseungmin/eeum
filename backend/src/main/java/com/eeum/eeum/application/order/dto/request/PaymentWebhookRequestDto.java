package com.eeum.eeum.application.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PortOne Webhook 요청")
public class PaymentWebhookRequestDto {

    @Schema(description = "PortOne paymentId", example = "pay_20260528130000_ABCD1234")
    private String paymentId;
}
