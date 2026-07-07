package com.eeum.eeum.application.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "AI 플랜 결제 완료 검증 요청")
public class AiPlanPaymentCompleteRequestDto {

    @NotBlank
    @Schema(description = "PortOne 결제 ID", example = "ai-plan-1-a1b2c3d4")
    private String paymentId;
}
