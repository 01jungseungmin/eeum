package com.eeum.eeum.application.owner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "사장 고객 요약 통계 응답")
public class OwnerCustomerSummaryResponseDto {

    @Schema(description = "전체 고객 수 (주문·찜·채팅 참여 합집합)", example = "120")
    private long totalCustomerCount;

    @Schema(description = "단골 고객 수 (최근 6개월 3회 이상 완료 주문)", example = "15")
    private long regularCustomerCount;

    @Schema(description = "일반 고객 수 (완료 주문 2회 이상, 단골 기준 미충족)", example = "50")
    private long normalCustomerCount;

    @Schema(description = "신규 고객 수 (완료 주문 1회)", example = "30")
    private long newCustomerCount;

    @Schema(description = "잠재 고객 수 (완료 주문 없음, 찜 또는 채팅 참여)", example = "25")
    private long potentialCustomerCount;

    @Schema(description = "찜 고객 수", example = "50")
    private long favoriteCustomerCount;

    @Schema(description = "채팅 참여 고객 수", example = "40")
    private long chatParticipantCustomerCount;

    @Schema(description = "전체 완료 주문 누적 매출", example = "3500000.00")
    private BigDecimal totalSalesAmount;
}
