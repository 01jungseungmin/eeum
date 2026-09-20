package com.eeum.eeum.application.owner.dto.response;

import com.eeum.eeum.domain.order.repository.CategorySalesStat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Builder
@Schema(description = "카테고리별 판매 통계")
public class OwnerCategorySalesResponseDto {

    @Schema(description = "상품 카테고리 ID", example = "3")
    private final Long categoryId;

    @Schema(description = "상품 카테고리명", example = "반찬류")
    private final String categoryName;

    @Schema(description = "판매 수량 합계. 이벤트 상품 판매분 포함", example = "137")
    private final Long soldQuantity;

    @Schema(description = "판매 금액 합계(단가 × 수량). 이벤트 상품 판매분 포함", example = "548000.00")
    private final BigDecimal salesAmount;

    @Schema(description = "전체 판매 수량 대비 비율(%). 소수 첫째 자리 반올림", example = "55.0")
    private final BigDecimal quantityRatio;

    @Schema(description = "전체 판매 금액 대비 비율(%). 소수 첫째 자리 반올림", example = "48.2")
    private final BigDecimal amountRatio;

    public static OwnerCategorySalesResponseDto of(
            CategorySalesStat stat,
            long totalQuantity,
            BigDecimal totalAmount
    ) {
        return OwnerCategorySalesResponseDto.builder()
                .categoryId(stat.categoryId())
                .categoryName(stat.categoryName())
                .soldQuantity(stat.soldQuantity())
                .salesAmount(stat.salesAmount())
                .quantityRatio(percent(BigDecimal.valueOf(stat.soldQuantity()), BigDecimal.valueOf(totalQuantity)))
                .amountRatio(percent(stat.salesAmount(), totalAmount))
                .build();
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
