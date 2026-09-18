package com.eeum.eeum.application.order.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PortOne 취소 API 응답 본문.
 *
 * v2는 cancellation 객체로 취소 건을 돌려주고, 상태에 따라 Succeeded/Requested/Failed로
 * 나뉘지만 id와 status는 공통이다. 금액 필드명이 문서상 확정적이지 않아 두 이름을 모두 받는다
 * — 응답 형태가 바뀌어 금액을 못 읽으면 정상 취소가 전부 수동 검토로 떨어지는 편이 더 위험하다.
 * 실제 매핑은 PortOnePaymentClientContractTest가 고정한다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortOneCancelResponse {

    private Cancellation cancellation;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cancellation {
        private String id;
        private String pgCancellationId;
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal cancelledAmount;

        /** 이번 취소로 처리된 금액. 어느 이름으로 오든 하나로 읽는다. 없으면 null. */
        public BigDecimal resolveCancelledAmount() {
            return totalAmount != null ? totalAmount : cancelledAmount;
        }
    }
}
