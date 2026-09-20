package com.eeum.eeum.application.owner.dto.response;

import com.eeum.eeum.domain.order.repository.CategorySalesStat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerCategorySalesResponseDtoTest {

    @Test
    void 수량_비율과_금액_비율을_각각_소수_첫째_자리로_반올림한다() {
        CategorySalesStat stat = new CategorySalesStat(1L, "음료", 6L, new BigDecimal("22000"));

        OwnerCategorySalesResponseDto dto =
                OwnerCategorySalesResponseDto.of(stat, 8L, new BigDecimal("36000"));

        assertThat(dto.getQuantityRatio()).isEqualByComparingTo("75.0");
        assertThat(dto.getAmountRatio()).isEqualByComparingTo("61.1");
    }

    @Test
    void 전체_금액이_0이면_비율은_0이다() {
        CategorySalesStat stat = new CategorySalesStat(1L, "무료 나눔", 3L, BigDecimal.ZERO);

        OwnerCategorySalesResponseDto dto =
                OwnerCategorySalesResponseDto.of(stat, 3L, BigDecimal.ZERO);

        assertThat(dto.getQuantityRatio()).isEqualByComparingTo("100.0");
        assertThat(dto.getAmountRatio()).isEqualByComparingTo("0");
    }
}
