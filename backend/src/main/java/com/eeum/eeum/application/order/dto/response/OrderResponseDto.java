package com.eeum.eeum.application.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "주문 응답")
public class OrderResponseDto {

    @Schema(description = "주문 ID", example = "1")
    private Long orderId;

    @Schema(description = "주문 번호", example = "ORD-20260528130000-ABCD1234")
    private String orderNumber;

    @Schema(description = "상점 ID", example = "1")
    private Long storeId;

    @Schema(description = "상점명", example = "골드락 카페")
    private String storeName;

    @Schema(description = "주문 유형", example = "SALE")
    private String orderType;

    @Schema(description = "주문 상태", example = "PENDING")
    private String status;

    @Schema(description = "결제 상태", example = "PENDING")
    private String paymentStatus;

    @Schema(description = "결제 수단", example = "CARD")
    private String paymentMethod;

    @Schema(description = "총 주문 금액", example = "32200")
    private BigDecimal totalPrice;

    @Schema(description = "주문 상품 목록")
    private List<OrderItemResponseDto> items;

    @Schema(description = "픽업 예정 시간", example = "2026-05-28T13:00:00")
    private LocalDateTime pickupScheduledAt;

    @Schema(description = "요청사항", example = "봉투에 담아주세요.")
    private String requestMessage;

    @Schema(description = "결제 완료 시간")
    private LocalDateTime paidAt;

    @Schema(description = "주문 생성 시간")
    private LocalDateTime createdAt;
}