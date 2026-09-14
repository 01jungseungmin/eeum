package com.eeum.eeum.application.order.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** PortOne 취소 응답 본문. 상태별 취소 객체와 두 금액 필드를 호환해 매핑한다. */
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
