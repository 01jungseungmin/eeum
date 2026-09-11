package com.eeum.eeum.application.order.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

/**
 * PortOne 취소 API 응답 본문.
 *
 * <p>PortOne v2는 {@code { "cancellation": { ... } }} 형태로 취소 건을 돌려준다.
 * 필드 구성은 리뷰 시점의 공식 문서와 다시 대조한다.
 */
@Getter
@NoArgsConstructor
public class PortOneCancelResponse {

    private Cancellation cancellation;

    @Getter
    @NoArgsConstructor
    public static class Cancellation {
        private String id;
        private String status;
        @JsonAlias({"amount", "cancelledAmount"})
        private BigDecimal cancelledAmount;
    }
}
