package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiGeneratedMessageTest {

    private AiGeneratedMessage draftMessage() {
        return AiGeneratedMessage.createDraft(
                mock(Store.class), mock(Account.class), AiMessageType.CUSTOMER_CARE,
                "TARGET", null, "제목", "내용", AiChannel.APP_PUSH);
    }

    @Test
    void SCHEDULED_상태에서_markFailed를_호출하면_FAILED로_전이된다() {
        // given
        AiGeneratedMessage message = draftMessage();
        message.edit("제목", "내용");
        message.schedule(LocalDateTime.now().plusMinutes(10), LocalDateTime.now());

        // when
        message.markFailed();

        // then
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.FAILED);
    }

    @Test
    void 이미_발송_완료된_메시지는_markFailed_호출에도_SENT_상태를_유지한다() {
        // given — 디스패치 성공 후 뒤늦게 도착한 실패 기록 호출을 시뮬레이션(동시성 경합)
        AiGeneratedMessage message = draftMessage();
        message.edit("제목", "내용");
        message.schedule(LocalDateTime.now().plusMinutes(10), LocalDateTime.now());
        message.send(LocalDateTime.now());

        // when
        message.markFailed();

        // then
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.SENT);
    }

    @Test
    void 이미_취소된_메시지는_markFailed_호출에도_CANCELLED_상태를_유지한다() {
        // given — 사장이 취소한 직후 뒤늦게 도착한 실패 기록 호출을 시뮬레이션(동시성 경합)
        AiGeneratedMessage message = draftMessage();
        message.edit("제목", "내용");
        message.schedule(LocalDateTime.now().plusMinutes(10), LocalDateTime.now());
        message.cancel();

        // when
        message.markFailed();

        // then
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.CANCELLED);
    }
}
