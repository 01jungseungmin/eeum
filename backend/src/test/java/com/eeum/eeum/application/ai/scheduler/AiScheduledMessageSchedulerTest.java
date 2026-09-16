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
    void 예약_시간이_지난_메시지는_발송_성공_후_SENT로_확정된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = scheduledMessage(1L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(message));
        when(processor.isDispatchable(eq(1L), any())).thenReturn(true);

        // when
        scheduler.dispatchScheduledMessages();

        // then — dispatch가 먼저, markSent(SENT 확정)는 그 이후
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(dispatchService, processor);
        inOrder.verify(dispatchService).dispatch(1L);
        inOrder.verify(processor).markSent(eq(1L), any());
    }

    @Test
    void 상태_재확인에서_발송_불가로_판정되면_발송하지_않는다() {
        // given — 예약 후 취소/수정된 메시지
        stubLockPassThrough();
        AiGeneratedMessage message = scheduledMessage(1L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(message));
        when(processor.isDispatchable(eq(1L), any())).thenReturn(false);

        // when
        scheduler.dispatchScheduledMessages();

        // then
        verify(dispatchService, never()).dispatch(anyLong());
        verify(processor, never()).markSent(anyLong(), any());
    }

    @Test
    void 발송_실패_시_SENT로_전이되지_않고_재시도_기록이_남으며_다른_메시지_처리는_계속된다() {
        // given — dispatch() 자체가 예외를 던지는 상황(치명적 실패). transitionToSent가 먼저 SENT로 바꿔버리면
        // recordFailure가 상태 가드에 막혀 무시되던 버그를 재현/검증한다.
        stubLockPassThrough();
        AiGeneratedMessage failing = scheduledMessage(1L);
        AiGeneratedMessage next = scheduledMessage(2L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(failing, next));
        when(processor.isDispatchable(eq(1L), any())).thenReturn(true);
        when(processor.isDispatchable(eq(2L), any())).thenReturn(true);
        doThrow(new RuntimeException("DB 오류")).when(dispatchService).dispatch(1L);

        // when
        scheduler.dispatchScheduledMessages();

        // then — 실패한 메시지는 SENT로 전이되지 않고(markSent 미호출) 재시도 카운트만 증가, 다음 메시지는 정상 처리
        verify(processor, never()).markSent(eq(1L), any());
        verify(processor).recordFailure(1L, 3);
        verify(dispatchService).dispatch(2L);
        verify(processor).markSent(eq(2L), any());
    }

    @Test
    void 메시지별_발송_락_경합은_재시도_카운트를_소진시키지_않는다() {
        // given — dispatchService.dispatch()가 (메시지 단위) 락 경합으로 LOCK_ACQUIRE_FAILED를 던지는 상황.
        // 이건 실제 발송 실패가 아니라 일시적 경합이므로 recordFailure로 재시도 카운트를 깎으면 안 된다.
        stubLockPassThrough();
        AiGeneratedMessage message = scheduledMessage(1L);
        when(aiGeneratedMessageRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(AiMessageStatus.SCHEDULED), any(), any())).thenReturn(List.of(message));
        when(processor.isDispatchable(eq(1L), any())).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED)).when(dispatchService).dispatch(1L);

        // when
        scheduler.dispatchScheduledMessages();

        // then
        verify(processor, never()).markSent(eq(1L), any());
        verify(processor, never()).recordFailure(anyLong(), anyInt());
    }

    @Test
    void 다른_인스턴스가_실행_중이면_락_획득_실패로_조용히_스킵한다() {
        // given
        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));

        // when & then — 예외가 전파되지 않아야 한다
        assertThatCode(() -> scheduler.dispatchScheduledMessages()).doesNotThrowAnyException();
        verify(processor, never()).isDispatchable(anyLong(), any());
    }
}
