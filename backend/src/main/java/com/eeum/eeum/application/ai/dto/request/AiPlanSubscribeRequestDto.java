package com.eeum.eeum.application.ai.dto.request;

import com.eeum.eeum.domain.ai.enums.AiPlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "AI 플랜 구독 요청")
public class AiPlanSubscribeRequestDto {

    @NotNull
    @Schema(description = "구독할 플랜 (BASIC/PRO)", example = "BASIC")
    private AiPlanType planType;
}
