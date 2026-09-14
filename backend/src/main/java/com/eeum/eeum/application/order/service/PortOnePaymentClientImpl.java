package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.response.PortOneCancelResponse;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentResponse;
import com.eeum.eeum.config.PortOneProperties;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.PortOnePaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * PortOne 호출 어댑터.
 *
 * <p>책임은 외부 호출과 응답 해석까지다. 실패 이력은 남기지 않고
 * {@link PortOnePaymentException}으로 변환해 던지기만 한다 —
 * 기록은 업무 맥락을 아는 서비스 계층이 한 번만 한다.
 */
@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class PortOnePaymentClientImpl implements PortOnePaymentClient {

    private final PortOneProperties portOneProperties;

    @Override
    public PortOnePaymentInfo getPayment(String paymentId) {
        PortOnePaymentResponse response;
        try {
            response = RestClient.create(portOneProperties.baseUrl())
                    .get()
                    .uri("/payments/{paymentId}", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + portOneProperties.apiSecret())
                    .retrieve()
                    .body(PortOnePaymentResponse.class);
        } catch (RuntimeException e) {
            // 통신 오류·타임아웃·역직렬화 실패를 한 타입으로 모은다.
            log.error("PortOne 결제 조회 실패: paymentId={}", paymentId, e);
            throw new PortOnePaymentException(ErrorCode.PAYMENT_VERIFY_FAILED,
                    "PortOne 결제 조회 실패: paymentId=" + paymentId, e);
        }

        if (response == null || response.getAmount() == null) {
            log.warn("결제 검증 실패 — PortOne 응답이 비어있음: paymentId={}, responseNull={}",
                    paymentId, response == null);
            throw new PortOnePaymentException(ErrorCode.PAYMENT_VERIFY_FAILED,
                    "PortOne 응답이 비어 있음: paymentId=" + paymentId);
        }

        return PortOnePaymentInfo.builder()
                .paymentId(response.getId())
                .status(response.getStatus())
                .amount(response.getAmount().getTotal())
                .cancelledAmount(response.getCancelledAmount())
                .pgProvider(response.getPgProvider())
                .build();
    }

    @Override
    public PortOneCancelResult cancelPayment(String paymentId, BigDecimal amount, String reason, String idempotencyKey) {
        // 금액 변환은 외부 호출이 아니다 — try 밖에 두어 PortOne 실패로 오분류되지 않게 한다.
        long cancelAmount = toPortOneAmount(amount);

        PortOneCancelResponse response;
        try {
            // 응답 본문을 버리지 않는다. 취소 상태(SUCCEEDED/REQUESTED/FAILED)와 취소 식별자가
            // 여기에만 있고, REQUESTED를 완료로 확정하면 미완료 취소가 완료로 기록된다.
            RestClient.RequestBodySpec request = RestClient.create(portOneProperties.baseUrl())
                    .post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + portOneProperties.apiSecret());
            if (idempotencyKey != null) {
                request.header("Idempotency-Key", "\"" + idempotencyKey + "\"");
            }
            response = request
                    .body(Map.of(
                            "reason", reason,
                            "amount", cancelAmount
                    ))
                    .retrieve()
                    .body(PortOneCancelResponse.class);

        } catch (RuntimeException e) {
            log.error("PortOne 결제 취소 실패: paymentId={}", paymentId, e);
            throw new PortOnePaymentException(ErrorCode.PAYMENT_REFUND_FAILED,
                    "PortOne 결제 취소 실패: paymentId=" + paymentId
                            + ", amount=" + amount + ", reason=" + reason, e);
        }

        if (response == null || response.getCancellation() == null) {
            // 상태를 알 수 없는 응답을 성공으로 넘기면 취소되지 않은 결제가 취소로 기록된다.
            log.warn("PortOne 취소 응답이 비어 있음: paymentId={}", paymentId);
            throw new PortOnePaymentException(ErrorCode.PAYMENT_REFUND_FAILED,
                    "PortOne 취소 응답이 비어 있음: paymentId=" + paymentId);
        }

        PortOneCancelResponse.Cancellation cancellation = response.getCancellation();
        return new PortOneCancelResult(
                cancellation.getStatus(),
                cancellation.getId() != null ? cancellation.getId() : cancellation.getPgCancellationId(),
                cancellation.resolveCancelledAmount());
    }

    private long toPortOneAmount(BigDecimal amount) {
        try {
            return amount.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }
    }
}
