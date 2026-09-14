package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.config.SettlementFeeProperties;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 결제 금액을 수수료와 지급액으로 나눈다.
 *
 * 수수료율은 SettlementFeeProperties가 들고 있고 이 클래스는 계산만 한다.
 * 수수료 체계를 바꿀 때 손대야 하는 곳은 설정값 하나뿐이다.
 *
 * 지급액은 비율로 따로 계산하지 않고 뺄셈으로만 만든다. DB CHECK 제약
 * (payout = payment - pg - platform)을 반올림 오차 없이 항상 만족시키기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class SettlementFeePolicy {

    /** DECIMAL(10,2) 컬럼에 맞춘 금액 스케일. */
    private static final int AMOUNT_SCALE = 2;

    private final SettlementFeeProperties properties;

    public Breakdown breakdown(BigDecimal paymentAmount) {
        if (paymentAmount == null || paymentAmount.signum() < 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_AMOUNT);
        }
        BigDecimal payment = paymentAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return breakdown(payment, properties.pgRate(), properties.platformRate());
    }

    /** 기존 원장의 요율을 재사용해 부분 취소 금액을 계산한다. */
    public Breakdown breakdown(BigDecimal paymentAmount, BigDecimal pgRate, BigDecimal platformRate) {
        if (paymentAmount == null || paymentAmount.signum() < 0
                || pgRate == null || platformRate == null
                || pgRate.signum() < 0 || platformRate.signum() < 0
                || pgRate.add(platformRate).compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_AMOUNT);
        }
        BigDecimal payment = paymentAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal pgFee = applyRate(payment, pgRate);

        // 두 수수료를 각각 반올림하면 합이 결제액을 1원 미만 넘길 수 있다(비율 합이 1에 가까울 때).
        // 그 몫은 플랫폼 수수료에서 깎아 지급액이 음수가 되지 않게 한다.
        BigDecimal platformFeeCap = payment.subtract(pgFee);
        BigDecimal platformFee = applyRate(payment, platformRate).min(platformFeeCap);

        return new Breakdown(payment, pgFee, platformFee, payment.subtract(pgFee).subtract(platformFee),
                pgRate, platformRate);
    }

    private BigDecimal applyRate(BigDecimal payment, BigDecimal rate) {
        return payment.multiply(rate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP).min(payment);
    }

    /** 한 결제 건의 금액 분해 결과. 네 값은 항상 payout = payment - pg - platform을 만족한다. */
    public record Breakdown(
            BigDecimal paymentAmount,
            BigDecimal pgFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal payoutAmount,
            BigDecimal pgRate,
            BigDecimal platformRate
    ) {
    }
}
