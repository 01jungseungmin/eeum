package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "이벤트 성과 및 다음 이벤트 추천")
public class AiEventPerformanceResponseDto {

    @Schema(description = "상품 조회수 (수집 불가 시 null)")
    private final Long productViewCount;

    @Schema(description = "이벤트 기간 주문 수", example = "12")
    private final long eventOrderCount;

    @Schema(description = "주문 전환율 (계산 불가 시 null)")
    private final Double orderConversionRate;

    @Schema(description = "신규 고객 비중 (계산 불가 시 null)")
    private final Double newCustomerRatio;

    @Schema(description = "단골 재주문 수", example = "4")
    private final long regularReorderCount;

    @Schema(description = "AI 성과 요약")
    private final String aiSummary;

    @Schema(description = "다음 이벤트 추천")
    private final NextEventRecommendationDto nextEventRecommendation;

    @Schema(description = "데이터 존재 여부", example = "true")
    private final boolean hasData;

    @Schema(description = "데이터 없음 안내 문구")
    private final String emptyMessage;


}
