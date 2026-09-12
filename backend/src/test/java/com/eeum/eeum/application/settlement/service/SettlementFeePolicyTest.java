package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.config.SettlementFeeProperties;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementFeePolicyTest {

    @Test
    void 수수료율이_0이면_결제액_전부가_지급액이_된다() {
        // given
        SettlementFeePolicy policy = policy("0", "0");

        // when
        SettlementFeePolicy.Breakdown breakdown = policy.breakdown(new BigDecimal("10000"));

        // then
        assertThat(breakdown.pgFeeAmount()).isEqualByComparingTo("0");
        assertThat(breakdown.platformFeeAmount()).isEqualByComparingTo("0");
        assertThat(breakdown.payoutAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void 설정값만_바꾸면_수수료가_그대로_반영된다() {
        // given — 코드 수정 없이 비율만 바꾼 상황
        SettlementFeePolicy policy = policy("0.033", "0.05");

        // when
        SettlementFeePolicy.Breakdown breakdown = policy.breakdown(new BigDecimal("10000"));

        // then
        assertThat(breakdown.pgFeeAmount()).isEqualByComparingTo("330.00");
        assertThat(breakdown.platformFeeAmount()).isEqualByComparingTo("500.00");
        assertThat(breakdown.payoutAmount()).isEqualByComparingTo("9170.00");
    }

    @Test
    void 지급액은_항상_결제액에서_수수료를_뺀_값과_같다() {
        // given — DB CHECK 제약(payout = payment - pg - platform)을 반올림이 깨뜨리면 안 된다
        SettlementFeePolicy policy = policy("0.0333", "0.0777");

        // when
        SettlementFeePolicy.Breakdown breakdown = policy.breakdown(new BigDecimal("12345"));

        // then
        assertThat(breakdown.paymentAmount()
                .subtract(breakdown.pgFeeAmount())
                .subtract(breakdown.platformFeeAmount()))
                .isEqualByComparingTo(breakdown.payoutAmount());
    }

    @Test
    void 수수료율_합이_1이어도_지급액이_음수가_되지_않는다() {
        // given — 각각 반올림하면 합이 결제액을 넘길 수 있는 경계
        SettlementFeePolicy policy = policy("0.5", "0.5");

        // when
        SettlementFeePolicy.Breakdown breakdown = policy.breakdown(new BigDecimal("0.01"));

        // then
        assertThat(breakdown.payoutAmount().signum()).isNotNegative();
        assertThat(breakdown.pgFeeAmount().add(breakdown.platformFeeAmount()))
                .isLessThanOrEqualTo(breakdown.paymentAmount());
    }

    @Test
    void 음수_결제액은_분해할_수_없다() {
        assertThatThrownBy(() -> policy("0", "0").breakdown(new BigDecimal("-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_AMOUNT);
    }

    @Test
    void 수수료율_합이_1을_넘으면_기동_시점에_막는다() {
        assertThatThrownBy(() -> new SettlementFeeProperties(new BigDecimal("0.7"), new BigDecimal("0.4")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수수료율을_설정하지_않으면_0으로_동작한다() {
        // given / when
        SettlementFeeProperties properties = new SettlementFeeProperties(null, null);

        // then
        assertThat(properties.pgRate()).isEqualByComparingTo("0");
        assertThat(properties.platformRate()).isEqualByComparingTo("0");
    }

    private SettlementFeePolicy policy(String pgRate, String platformRate) {
        return new SettlementFeePolicy(
                new SettlementFeeProperties(new BigDecimal(pgRate), new BigDecimal(platformRate)));
    }
}
