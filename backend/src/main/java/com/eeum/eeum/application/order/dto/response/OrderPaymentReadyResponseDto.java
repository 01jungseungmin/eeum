package com.eeum.eeum.application.order.dto.response;

import com.eeum.eeum.domain.order.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "주문 생성 및 결제 준비 응답")
public class OrderPaymentReadyResponseDto {

    @Schema(description = "주문 ID", example = "1")
    private Long orderId;

    @Schema(description = "주문 번호", example = "ORD-20260528130000-ABCD1234")
    private String orderNumber;

    @Schema(description = "PortOne 결제 ID. 현장결제는 null일 수 있습니다.", example = "pay_20260528130000_ABCD1234")
    private String paymentId;

    @Schema(description = "결제 표시용 주문명", example = "김치찌개 외 2건")
    private String orderName;

    @Schema(description = "상점 ID", example = "1")
    private Long storeId;

    @Schema(description = "상점명", example = "골드락 카페")
    private String storeName;

    @Schema(description = "결제 수단", example = "CARD")
    private PaymentMethod paymentMethod;

    @Schema(description = "총 결제 금액", example = "32200")
    private BigDecimal totalPrice;

    @Schema(description = "픽업 예정 시간", example = "2026-05-28T13:00:00")
    private LocalDateTime pickupScheduledAt;
}