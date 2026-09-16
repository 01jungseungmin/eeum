package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiCareType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 고객 케어 카드")
public class AiCustomerCareCardDto {

    @Schema(description = "케어 유형", example = "CART_INTEREST")
    private final AiCareType careType;

    @Schema(description = "카드 제목", example = "구매 관심이 높은 고객")
    private final String title;

    @Schema(description = "우선순위 (낮을수록 높음)", example = "1")
    private final int priority;

    @Schema(description = "대상 고객 수", example = "5")
    private final int targetCustomerCount;

    @Schema(description = "AI 판단 이유")
    private final String reason;

    @Schema(description = "AI가 준비한 메시지 초안")
    private final String preparedMessage;

    @Schema(description = "최근 7일 내 알림을 받아 제외된 고객 수", example = "2")
    private final int recentlyNotifiedExcludedCount;

    @Schema(description = "발송 가능 여부", example = "true")
    private final boolean sendable;
}
