package com.eeum.eeum.application.ai.listener;

import com.eeum.eeum.application.ai.service.AiMessageCommandExecutor;
import com.eeum.eeum.application.ai.service.AiMessageDispatchService;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.event.AiMessageSentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AiMessageEventListenerTest {

    @InjectMocks
    private AiMessageEventListener listener;

    @Mock private AiMessageDispatchService aiMessageDispatchService;
    @Mock private AiMessageCommandExecutor aiMessageCommandExecutor;

    private static final Long MESSAGE_ID = 1L;

    private AiMessageSentEvent event(boolean scheduled) {
        return new AiMessageSentEvent(
                MESSAGE_ID, 5L, AiMessageType.EVENT_MARKETING, AiChannel.APP_PUSH, scheduled, LocalDateTime.now());
    }

    @Test
    void 즉시_발송_이벤트면_디스패치를_호출한다() {
        // given & when
        listener.handleAiMessageSent(event(false));

        // then
        verify(aiMessageDispatchService).dispatch(eq(MESSAGE_ID));
        verifyNoMoreInteractions(aiMessageDispatchService);
        verifyNoInteractions(aiMessageCommandExecutor);
    }

    @Test
    void 예약_발송_이벤트면_디스패치하지_않고_스케줄러에_맡긴다() {
        // given & when
        listener.handleAiMessageSent(event(true));

        // then
        verifyNoInteractions(aiMessageDispatchService, aiMessageCommandExecutor);
    }

    @Test
    void 디스패치_실패_시_메시지를_FAILED로_전이하고_예외는_전파되지_않는다() {
        // given
        doThrow(new RuntimeException("발송 실패")).when(aiMessageDispatchService).dispatch(MESSAGE_ID);

        // when & then
        assertThatCode(() -> listener.handleAiMessageSent(event(false)))
                .doesNotThrowAnyException();
        verify(aiMessageCommandExecutor).markFailedInTx(eq(MESSAGE_ID));
    }

    @Test
    void FAILED_전이마저_실패해도_예외가_전파되지_않는다() {
        // given
        doThrow(new RuntimeException("발송 실패")).when(aiMessageDispatchService).dispatch(MESSAGE_ID);
        doThrow(new RuntimeException("상태 전이 실패")).when(aiMessageCommandExecutor).markFailedInTx(MESSAGE_ID);

        // when & then
        assertThatCode(() -> listener.handleAiMessageSent(event(false)))
                .doesNotThrowAnyException();
    }
}
