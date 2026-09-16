package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCleanupSchedulerTest {

    @InjectMocks
    private AiCleanupScheduler scheduler;

    @Mock
    private AiChatMessageRepository aiChatMessageRepository;

    @Test
    void 실행_시_90일_이전_AI_채팅_메시지를_삭제한다() {
        // given
        when(aiChatMessageRepository.deleteOldMessages(any())).thenReturn(3);
        LocalDateTime before = LocalDateTime.now().minusDays(90).minusMinutes(1);

        // when
        scheduler.cleanupOldAiChatMessages();

        // then
        LocalDateTime after = LocalDateTime.now().minusDays(90).plusMinutes(1);
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(aiChatMessageRepository).deleteOldMessages(thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isBetween(before, after);
        verifyNoMoreInteractions(aiChatMessageRepository);
    }
}
