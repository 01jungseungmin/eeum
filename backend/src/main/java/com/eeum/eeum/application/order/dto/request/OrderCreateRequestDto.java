package com.eeum.eeum.application.order.dto.request;

import com.eeum.eeum.domain.order.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "주문 생성 요청")
public class OrderCreateRequestDto {

    @NotNull(message = "결제 수단은 필수입니다.")
    @Schema(
            description = "결제 수단",
            example = "CARD",
            allowableValues = {"CARD", "EASY_PAY", "TRANSFER", "VIRTUAL_ACCOUNT", "CASH_ON_SITE"}
    )
    private PaymentMethod paymentMethod;

    @Schema(description = "픽업 예정 시간", example = "2026-05-28T13:00:00")
    private LocalDateTime pickupScheduledAt;

    @Size(max = 500, message = "요청사항은 500자 이하로 입력해야 합니다.")
    @Schema(description = "요청사항", example = "봉투에 담아주세요.")
    private String requestMessage;
}