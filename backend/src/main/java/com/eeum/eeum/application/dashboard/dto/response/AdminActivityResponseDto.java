package com.eeum.eeum.application.dashboard.dto.response;

import com.eeum.eeum.application.dashboard.enums.DashboardActivityType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "관리자 대시보드 실시간 활동 한 건")
public class AdminActivityResponseDto {

    @Schema(description = "활동 종류", example = "PAYMENT_COMPLETED")
    private final DashboardActivityType type;

    @Schema(description = "활동 대상 ID. 종류에 따라 accountId·storeId·paymentId·reportId", example = "1024")
    private final Long targetId;

    @Schema(description = "제목", example = "거래 완료")
    private final String title;

    @Schema(description = "부가 설명. 가입 지역·상점명·신고 사유 등. 없으면 null", example = "온담 커피", nullable = true)
    private final String description;

    @Schema(description = "결제 금액. PAYMENT_COMPLETED에만 있음", example = "47500.00", nullable = true)
    private final BigDecimal amount;

    @Schema(description = "발생 시각")
    private final LocalDateTime occurredAt;
}
