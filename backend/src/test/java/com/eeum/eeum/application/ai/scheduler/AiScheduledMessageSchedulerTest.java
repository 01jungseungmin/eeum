package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiMessageDispatchService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScheduledMessageSchedulerTest {

    @InjectMocks
    private AiScheduledMessageScheduler scheduler;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiScheduledMessageProcessor processor;
    @Mock private AiMessageDispatchService dispatchService;
    @Mock private RedisLockService redisLockService;

    private void stubLockPassThrough() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));
    }

    private AiGeneratedMessage scheduledMessage(Long id) {
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                mock(Store.class), mock(Account.class), AiMessageType.NOTICE,
                null, null, "제목", "본문", AiChannel.APP_PUSH);
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", id);
        ReflectionTestUtils.setField(message, "status", AiMessageStatus.SCHEDULED);
        ReflectionTestUtils.setField(message, "scheduledAt", LocalDateTime.now().minusMinutes(5));
        return message;
    }

    @Test
    void 예약_시간이_지난_메시지는_SENT_전이_후_발송된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = scheduledMessage(1L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(message));
        when(processor.transitionToSent(eq(1L), any())).thenReturn(true);

        // when
        scheduler.dispatchScheduledMessages();

        // then
        verify(dispatchService).dispatch(1L);
    }

    @Test
    void 상태_재확인에서_발송_불가로_판정되면_발송하지_않는다() {
        // given — 예약 후 취소/수정된 메시지
        stubLockPassThrough();
        AiGeneratedMessage message = scheduledMessage(1L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(message));
        when(processor.transitionToSent(eq(1L), any())).thenReturn(false);

        // when
        scheduler.dispatchScheduledMessages();

        // then
        verify(dispatchService, never()).dispatch(anyLong());
    }

    @Test
    void 발송_실패_시_재시도_기록이_남고_다른_메시지_처리는_계속된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage failing = scheduledMessage(1L);
        AiGeneratedMessage next = scheduledMessage(2L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(failing, next));
        when(processor.transitionToSent(eq(1L), any())).thenThrow(new RuntimeException("DB 오류"));
        when(processor.transitionToSent(eq(2L), any())).thenReturn(true);

        // when
        scheduler.dispatchScheduledMessages();

        // then
        verify(processor).recordFailure(1L, 3);
        verify(dispatchService).dispatch(2L);
    }

    @Test
    void 다른_인스턴스가_실행_중이면_락_획득_실패로_조용히_스킵한다() {
        // given
        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));

        // when & then — 예외가 전파되지 않아야 한다
        assertThatCode(() -> scheduler.dispatchScheduledMessages()).doesNotThrowAnyException();
        verify(processor, never()).transitionToSent(anyLong(), any());
    }
}

@ExtendWith(MockitoExtension.class)
class AiScheduledMessageProcessorTest {

    @InjectMocks
    private AiScheduledMessageProcessor processor;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;

    private AiGeneratedMessage message(AiMessageStatus status, LocalDateTime scheduledAt) {
        Store store = mock(Store.class);
        Account owner = mock(Account.class);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.NOTICE, null, null, "제목", "본문", AiChannel.APP_PUSH);
        ReflectionTestUtils.setField(message, "status", status);
        ReflectionTestUtils.setField(message, "scheduledAt", scheduledAt);
        return message;
    }

    @Test
    void 예약_시간이_지난_SCHEDULED_메시지는_SENT로_전이된다() {
        // given
        AiGeneratedMessage target = message(AiMessageStatus.SCHEDULED, LocalDateTime.now().minusMinutes(1));
        when(aiGeneratedMessageRepository.findById(1L)).thenReturn(Optional.of(target));

        // when
        boolean transitioned = processor.transitionToSent(1L, LocalDateTime.now());

        // then
        assertThat(transitioned).isTrue();
        assertThat(target.getStatus()).isEqualTo(AiMessageStatus.SENT);
        verify(aiActionLogRepository).save(any());
    }

    @Test
    void 예약_시간이_아직_안_된_메시지는_발송하지_않는다() {
        // given
        AiGeneratedMessage target = message(AiMessageStatus.SCHEDULED, LocalDateTime.now().plusHours(1));
        when(aiGeneratedMessageRepository.findById(1L)).thenReturn(Optional.of(target));

        // when & then
        assertThat(processor.transitionToSent(1L, LocalDateTime.now())).isFalse();
        assertThat(target.getStatus()).isEqualTo(AiMessageStatus.SCHEDULED);
    }

    @Test
    void 이미_취소된_메시지는_발송하지_않는다() {
        // given
        AiGeneratedMessage target = message(AiMessageStatus.CANCELLED, LocalDateTime.now().minusMinutes(1));
        when(aiGeneratedMessageRepository.findById(1L)).thenReturn(Optional.of(target));

        // when & then
        assertThat(processor.transitionToSent(1L, LocalDateTime.now())).isFalse();
    }

    @Test
    void 발송_실패_시_재시도_횟수가_증가한다() {
        // given
        AiGeneratedMessage target = message(AiMessageStatus.SCHEDULED, LocalDateTime.now().minusMinutes(1));
        when(aiGeneratedMessageRepository.findById(1L)).thenReturn(Optional.of(target));

        // when
        processor.recordFailure(1L, 3);

        // then
        assertThat(target.getRetryCount()).isEqualTo(1);
        assertThat(target.getStatus()).isEqualTo(AiMessageStatus.SCHEDULED); // 아직 재시도 가능
    }

    @Test
    void 최대_재시도_초과_시_FAILED로_확정된다() {
        // given
        AiGeneratedMessage target = message(AiMessageStatus.SCHEDULED, LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(target, "retryCount", 2);
        when(aiGeneratedMessageRepository.findById(1L)).thenReturn(Optional.of(target));

        // when — 3회째 실패
        processor.recordFailure(1L, 3);

        // then
        assertThat(target.getRetryCount()).isEqualTo(3);
        assertThat(target.getStatus()).isEqualTo(AiMessageStatus.FAILED);
    }
}
