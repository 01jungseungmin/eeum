package com.eeum.eeum.application.order.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증상: PortOne Transaction.Cancelled 전체 payload가 400으로 거부된다.
 * 결함 위치: PaymentWebhookRequestDto.java.
 * 원인: 최신 data에는 paymentId 외 storeId, transactionId, cancellationId가 포함되지만 DTO가 이를 허용하지 않는다.
 * 기대: 알려지지 않은 필드는 무시하고 data.paymentId로 내부 결제 동기화를 계속한다.
 */
class PaymentWebhookRequestDtoTest {

    @Test
    void 최신_취소_Webhook의_추가_필드를_무시하고_paymentId를_읽는다() throws Exception {
        // given
        String payload = """
                {
                  "type":"Transaction.Cancelled",
                  "timestamp":"2024-04-25T10:00:00.000Z",
                  "data":{
                    "paymentId":"pay-1",
                    "storeId":"store-1",
                    "transactionId":"tx-1",
                    "cancellationId":"cancel-1"
                  }
                }
                """;

        // when
        PaymentWebhookRequestDto request = new ObjectMapper().readValue(payload, PaymentWebhookRequestDto.class);

        // then
        assertThat(request.resolvedPaymentId()).isEqualTo("pay-1");
    }
}
