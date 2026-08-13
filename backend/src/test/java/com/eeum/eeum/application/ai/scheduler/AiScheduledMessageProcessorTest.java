package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiMessageCommandExecutor;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScheduledMessageProcessorTest {

    @InjectMocks
    private AiScheduledMessageProcessor processor;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private AiMessageCommandExecutor messageCommandExecutor;

    private static final Long MESSAGE_ID = 1L;

    // ──────────────────── Helpers ────────────────────

    private AiGeneratedMessage scheduledMessage(LocalDateTime scheduledAt) {
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                mock(Store.class), mock(Account.class), AiMessageType.NOTICE,
                null, null, "제목", "내용", AiChannel.APP_PUSH);
        message.edit(null, null); // DRAFT → REVIEWED
        message.schedule(scheduledAt, scheduledAt.minusHours(1)); // REVIEWED → SCHEDULED
        return message;
    }

    // ──────────────────── isDispatchable ────────────────────

    @Test
    void 예약_시각이_지난_SCHEDULED_메시지는_발송_가능하다() {
        // given
        LocalDateTime scheduledAt = LocalDateTime.now().minusMinutes(5);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID))
                .thenReturn(Optional.of(scheduledMessage(scheduledAt)));

        // when & then
        assertThat(processor.isDispatchable(MESSAGE_ID, LocalDateTime.now())).isTrue();
    }

    @Test
    void 예약_시각이_아직_안_된_메시지는_발송_불가하다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID))
                .thenReturn(Optional.of(scheduledMessage(now.plusHours(1))));

        // when & then
        assertThat(processor.isDispatchable(MESSAGE_ID, now)).isFalse();
    }

    @Test
    void SCHEDULED_상태가_아닌_메시지는_발송_불가하다() {
        // given — DRAFT 상태
        AiGeneratedMessage draft = AiGeneratedMessage.createDraft(
                mock(Store.class), mock(Account.class), AiMessageType.NOTICE,
                null, null, "제목", "내용", AiChannel.APP_PUSH);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(draft));

        // when & then
        assertThat(processor.isDispatchable(MESSAGE_ID, LocalDateTime.now())).isFalse();
    }

    @Test
    void 메시지가_없으면_발송_불가하다() {
        // given
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.empty());

        // when & then
        assertThat(processor.isDispatchable(MESSAGE_ID, LocalDateTime.now())).isFalse();
    }

    // ──────────────────── markSent ────────────────────

    @Test
    void 발송_확정_시_SENT로_전이되고_발송_액션로그가_저장된다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        AiGeneratedMessage message = scheduledMessage(now.minusMinutes(5));
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        // when
        processor.markSent(MESSAGE_ID, now);

        // then
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.SENT);
        assertThat(message.getSentAt()).isEqualTo(now);
        assertThat(message.getScheduledAt()).isNull();
        verify(messageCommandExecutor).applyLinkedDomainSideEffectInCurrentTransaction(message);

        ArgumentCaptor<AiActionLog> logCaptor = ArgumentCaptor.forClass(AiActionLog.class);
        verify(aiActionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType()).isEqualTo(AiActionType.MESSAGE_SENT);
    }

    @Test
    void 발송_확정_시_메시지가_없으면_아무_일도_하지_않는다() {
        // given
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.empty());

        // when
        processor.markSent(MESSAGE_ID, LocalDateTime.now());

        // then
        verify(aiActionLogRepository, never()).save(any());
        verify(messageCommandExecutor, never()).applyLinkedDomainSideEffectInCurrentTransaction(any());
    }

    // ──────────────────── recordFailure ────────────────────

    @Test
    void 발송_실패_시_재시도_카운트가_증가하고_최대치_미만이면_SCHEDULED가_유지된다() {
        // given
        AiGeneratedMessage message = scheduledMessage(LocalDateTime.now().minusMinutes(5));
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        // when
        processor.recordFailure(MESSAGE_ID, 3);

        // then
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.SCHEDULED);
    }

    @Test
    void 재시도가_최대치에_도달하면_FAILED로_확정된다() {
        // given
        AiGeneratedMessage message = scheduledMessage(LocalDateTime.now().minusMinutes(5));
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        // when — 최대 3회까지 실패 반복
        processor.recordFailure(MESSAGE_ID, 3);
        processor.recordFailure(MESSAGE_ID, 3);
        processor.recordFailure(MESSAGE_ID, 3);

        // then
        assertThat(message.getRetryCount()).isEqualTo(3);
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.FAILED);
    }

    @Test
    void SCHEDULED_상태가_아닌_메시지는_실패_기록이_무시된다() {
        // given — 이미 SENT 처리된 메시지
        AiGeneratedMessage message = scheduledMessage(LocalDateTime.now().minusMinutes(5));
        message.send(LocalDateTime.now());
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        // when
        processor.recordFailure(MESSAGE_ID, 3);

        // then
        assertThat(message.getRetryCount()).isZero();
        assertThat(message.getStatus()).isEqualTo(AiMessageStatus.SENT);
    }

    @Test
    void 실패_기록_시_메시지가_없으면_아무_일도_하지_않는다() {
        // given
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.empty());

        // when
        processor.recordFailure(MESSAGE_ID, 3);

        // then
        verify(aiActionLogRepository, never()).save(any());
    }
}
