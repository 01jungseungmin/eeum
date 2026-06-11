package com.eeum.eeum.application.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "결제 완료 검증 요청")
public class PaymentCompleteRequestDto {

    @NotBlank(message = "결제 ID는 필수입니다.")
    @Schema(description = "PortOne paymentId", example = "pay_20260528130000_ABCD1234")
    private String paymentId;

    @NotBlank(message = "주문 번호는 필수입니다.")
    @Schema(description = "주문 번호", example = "ORD-20260528130000-ABCD1234")
    private String orderNumber;
}