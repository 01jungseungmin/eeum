package com.eeum.eeum.application.ai.dto.request;

import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "생활권 매칭 조건 변경 요청")
public class AiLocalMatchConditionRequestDto {

    @Schema(description = "반경 (km) — 1 / 1.5 / 3", example = "1.5")
    private Double radiusKm;

    @Size(max = 30)
    @Schema(description = "관심사", example = "한식")
    private String interest;

    @Schema(description = "고객 유형", example = "ALL")
    private AiCustomerType customerType;

    @AssertTrue(message = "radiusKm은 1, 1.5, 3 중 하나여야 합니다")
    @Schema(hidden = true)
    public boolean isValidRadius() {
        return radiusKm == null || radiusKm == 1.0 || radiusKm == 1.5 || radiusKm == 3.0;
    }
}
