package com.eeum.eeum.application.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "상품 관리 요약 응답")
public class ProductSummaryResponseDto {

    @Schema(description = "전체 상품 수", example = "6")
    private long totalCount;

    @Schema(description = "판매 상품 수", example = "4")
    private long saleCount;

    @Schema(description = "예약 상품 수", example = "1")
    private long reservationCount;

    @Schema(description = "메뉴 상품 수", example = "1")
    private long menuCount;

    @Schema(description = "판매중 상품 수", example = "5")
    private long activeCount;

    @Schema(description = "품절 상품 수", example = "1")
    private long soldOutCount;

    @Schema(description = "비공개 상품 수", example = "0")
    private long inactiveCount;
}