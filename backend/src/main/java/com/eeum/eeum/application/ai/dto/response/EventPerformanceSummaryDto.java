package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "이벤트 성과 요약")
    public class EventPerformanceSummaryDto {
        @Schema(description = "상품 조회수 (수집 불가 시 null)")
        private final Long productViewCount;
        @Schema(description = "주문 전환율 (계산 불가 시 null)")
        private final Double orderConversionRate;
        @Schema(description = "신규 고객 비중 (계산 불가 시 null)")
        private final Double newCustomerRatio;
        @Schema(description = "단골 재주문 수")
        private final long regularReorderCount;
    }