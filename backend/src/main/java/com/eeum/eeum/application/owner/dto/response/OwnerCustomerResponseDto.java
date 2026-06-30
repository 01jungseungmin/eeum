package com.eeum.eeum.application.owner.dto.response;

import com.eeum.eeum.application.owner.enums.OwnerCustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@Schema(description = "사장 통합 고객 목록 응답")
public class OwnerCustomerResponseDto {

    @Schema(description = "고객 계정 ID", example = "42")
    private Long customerId;

    @Schema(description = "고객명 (마스킹)", example = "이*민")
    private String maskedName;

    @Schema(description = "전화번호 (마스킹)", example = "010-****-5678")
    private String maskedPhone;

    @Schema(description = "고객 유형")
    private OwnerCustomerType customerType;

    @Schema(description = "상점 찜 여부")
    private boolean favorite;

    @Schema(description = "상점 채팅방 ACTIVE 참여 여부")
    private boolean chatParticipant;

    @Schema(description = "완료 주문 수", example = "3")
    private int totalOrderCount;

    @Schema(description = "완료 주문 누적 금액", example = "75000.00")
    private BigDecimal totalOrderAmount;

    @Schema(description = "이 상점에 남긴 리뷰 수", example = "2")
    private int reviewCount;

    @Schema(description = "이 상점에 남긴 평균 평점 (리뷰 없으면 null)", example = "4.5")
    private Double averageRating;

    @Schema(description = "최근 완료 주문일 (주문 없으면 null)", example = "2024-06-01")
    private LocalDate lastOrderDate;
}
