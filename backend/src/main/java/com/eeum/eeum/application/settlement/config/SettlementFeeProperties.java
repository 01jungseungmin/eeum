package com.eeum.eeum.application.settlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 정산 수수료율. 설정값만 바꾸면 수수료 체계가 바뀐다 — 코드 수정이 필요 없다.
 *
 * <p>비율은 0 이상 1 이하의 소수다. 예: {@code 0.033}이면 3.3%.
 * 현재 운영값은 둘 다 0(수수료 없음)이며, {@code application.yml}의
 * {@code settlement.fee.*} 또는 환경변수로 언제든 바꿀 수 있다.
 *
 * <p><b>주의</b>: 원장은 생성 시점의 금액 스냅샷이다. 비율을 바꾸면 그 이후 생성되는
 * 원장부터 적용되고, 이미 만들어진 원장은 바뀌지 않는다 — 소급 적용이 필요하면
 * 별도 보정 작업을 설계해야 한다.
 */
@ConfigurationProperties(prefix = "settlement.fee")
public record SettlementFeeProperties(
        BigDecimal pgRate,
        BigDecimal platformRate
) {

    public SettlementFeeProperties {
        pgRate = pgRate == null ? BigDecimal.ZERO : pgRate;
        platformRate = platformRate == null ? BigDecimal.ZERO : platformRate;

        // 합이 1을 넘으면 지급액이 음수가 된다. 기동 시점에 막는다.
        if (pgRate.signum() < 0 || platformRate.signum() < 0
                || pgRate.add(platformRate).compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "정산 수수료율은 0 이상이고 합이 1을 넘을 수 없습니다: pgRate=" + pgRate
                            + ", platformRate=" + platformRate);
        }
    }
}
