package com.eeum.eeum.application.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "주문 상품 응답")
public class OrderItemResponseDto {

    @Schema(description = "주문 상품 ID", example = "1")
    private Long orderItemId;

    @Schema(description = "상품 ID", example = "1")
    private Long productId;

    @Schema(description = "이벤트 상품 ID", example = "1")
    private Long eventProductId;

    @Schema(description = "상품 유형", example = "SALE")
    private String productType;

    @Schema(description = "상품명", example = "스프가 맛있는 우유")
    private String productName;

    @Schema(description = "상품 썸네일 URL")
    private String thumbnailUrl;

    @Schema(description = "선택 옵션 텍스트", example = "맵기: 매운맛, 용량: 500ml")
    private String selectedOptionsText;

    @Schema(description = "상품 기본 가격", example = "4400")
    private BigDecimal basePrice;

    @Schema(description = "옵션 추가 금액 합계", example = "500")
    private BigDecimal optionsTotalPrice;

    @Schema(description = "1개 기준 최종 단가", example = "4900")
    private BigDecimal unitPrice;

    @Schema(description = "수량", example = "2")
    private Integer quantity;

    @Schema(description = "상품 총 금액", example = "9800")
    private BigDecimal lineTotalPrice;
}