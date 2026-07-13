package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "고객 케어 카드 요약")
    public class CustomerCareSummaryDto {
        @Schema(description = "케어 유형", example = "CART_INTEREST")
        private final String careType;
        @Schema(description = "제목", example = "구매 관심이 높은 고객")
        private final String title;
        @Schema(description = "대상 고객 수", example = "5")
        private final int targetCustomerCount;
    }