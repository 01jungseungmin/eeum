package com.eeum.eeum.application.order.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PortOne 취소 API 응답 본문.
 *
 * <p>PortOne v2는 {@code { "cancellation": { ... } }} 형태로 취소 건을 돌려준다.
 * 취소 객체는 상태에 따라 {@code SucceededPaymentCancellation} /
 * {@code RequestedPaymentCancellation} / {@code FailedPaymentCancellation}로 나뉘고,
 * 공통으로 {@code id}와 {@code status}를 갖는다.
 *
 * <p><b>금액 필드명이 문서상 확정적이지 않아 두 이름을 모두 받는다.</b> 취소 금액은
 * {@code totalAmount}로 오는 것이 문서의 타입 정의에 가깝지만, 응답 형태가 바뀌었을 때
 * 금액을 읽지 못해 정상 취소가 전부 수동 검토로 떨어지는 편이 더 위험하다.
 * 실제 매핑은 {@code PortOnePaymentClientContractTest}가 고정한다.
 *
 * <p>{@code @JsonIgnoreProperties}로 모르는 필드를 무시한다 — PortOne이 필드를 추가해도
 * 역직렬화가 깨지지 않아야 한다.
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
