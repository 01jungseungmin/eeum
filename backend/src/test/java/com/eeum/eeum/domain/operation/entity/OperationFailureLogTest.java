package com.eeum.eeum.domain.operation.entity;

import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationFailureLogTest {

    @Test
    void 긴_에러메시지와_payload는_상한까지_잘라서_저장한다() {
        // given: 외부 응답 본문처럼 길이 제한이 없는 문자열
        String longMessage = "e".repeat(OperationFailureLog.MESSAGE_MAX_LENGTH + 500);
        String longPayload = "p".repeat(OperationFailureLog.PAYLOAD_MAX_LENGTH + 500);

        // when
        OperationFailureLog log = OperationFailureLog.create(
                OperationFailureCategory.EXTERNAL_API,
                "PortOnePaymentClient.getPayment",
                "PAYMENT", "pay-1",
                "PAYMENT_VERIFY_FAILED",
                longMessage,
                longPayload);

        // then: 자르지 않으면 이력 저장이 제약 위반으로 실패해 원래 장애까지 함께 묻힌다
        assertThat(log.getErrorMessage()).hasSize(OperationFailureLog.MESSAGE_MAX_LENGTH);
        assertThat(log.getPayload()).hasSize(OperationFailureLog.PAYLOAD_MAX_LENGTH);
    }

    @Test
    void 상한_이하_값과_null은_그대로_보존한다() {
        OperationFailureLog log = OperationFailureLog.create(
                OperationFailureCategory.SCHEDULER,
                "OrderExpirationScheduler.expireOrders",
                "SCHEDULER", null,
                "IllegalStateException",
                "커넥션 없음",
                null);

        assertThat(log.getErrorMessage()).isEqualTo("커넥션 없음");
        assertThat(log.getPayload()).isNull();
        assertThat(log.getRefId()).isNull();
        assertThat(log.getCategory()).isEqualTo(OperationFailureCategory.SCHEDULER);
    }
}
