package com.eeum.eeum.application.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "이벤트 상품 응답")
public class EventProductResponseDto {

    @Schema(description = "이벤트 상품 ID", example = "1")
    private Long eventProductId;

    @Schema(description = "상품 ID", example = "3")
    private Long productId;

    @Schema(description = "상품명", example = "불고기 반찬 300g")
    private String productName;

    @Schema(description = "원래 가격", example = "12000")
    private BigDecimal originalPrice;

    @Schema(description = "이벤트 가격", example = "8900")
    private BigDecimal eventPrice;

    @Schema(description = "할인율", example = "26")
    private Integer discountRate;

    @Schema(description = "이벤트 총 수량", example = "30")
    private Integer eventStock;

    @Schema(description = "판매 수량", example = "18")
    private Integer soldCount;

    @Schema(description = "잔여 수량", example = "12")
    private Integer remainingStock;

    @Schema(description = "이벤트 시작 일시", example = "2026-05-25T12:00:00")
    private LocalDateTime startAt;

    @Schema(description = "이벤트 종료 일시", example = "2026-05-25T18:00:00")
    private LocalDateTime endAt;

    @Schema(description = "활성 여부", example = "true")
    private boolean active;

    @Schema(description = "현재 진행 중 여부", example = "true")
    private boolean ongoing;

    @Schema(description = "이벤트 상태", example = "ONGOING", allowableValues = {"SCHEDULED", "ONGOING", "ENDED", "INACTIVE", "SOLD_OUT"})
    private String eventStatus;
}