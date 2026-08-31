package com.eeum.eeum.domain.notification.entity;

import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * outbox 행의 상태 전이.
 *
 * <p>재시도 한도가 없으면 깨진 행 하나가 주기마다 앞자리를 차지해 뒤의 정상 알림을 막는다.
 * 반대로 한도를 넘겨도 지우지 않는다 — 알림이 끝내 생성되지 않은 기록이라 조사 근거로 남긴다.
 */
class NotificationOutboxTest {

    private NotificationOutbox outbox() {
        return NotificationOutbox.pending("CHAT_MESSAGE_SENT", "{}");
    }

    @Test
    void 생성_직후에는_대기_상태다() {
        NotificationOutbox outbox = outbox();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getProcessedAt()).isNull();
    }

    @Test
    void 처리하면_완료로_바뀌고_시각이_남는다() {
        NotificationOutbox outbox = outbox();

        outbox.markDone();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(outbox.getProcessedAt()).isNotNull();
    }

    @Test
    void 실패하면_시도_횟수가_오르고_아직_재시도_대상이다() {
        NotificationOutbox outbox = outbox();

        outbox.recordFailure("boom");

        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.isExhausted()).isFalse();
    }

    @Test
    void 한도를_넘기면_더_시도하지_않는다() {
        // Given: 영원히 재시도하면 깨진 행이 주기마다 앞자리를 차지해 뒤의 알림을 막는다
        NotificationOutbox outbox = outbox();

        // When
        for (int attempt = 0; attempt < 5; attempt++) {
            outbox.recordFailure("boom");
        }

        // Then
        assertThat(outbox.isExhausted()).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void 긴_오류_메시지는_잘라_담는다() {
        // Given: 스택 문자열이 그대로 들어오면 컬럼 길이를 넘겨 INSERT가 깨진다
        NotificationOutbox outbox = outbox();

        // When
        outbox.recordFailure("x".repeat(5000));

        // Then
        assertThat(outbox.getLastError()).hasSize(1000);
    }
}
