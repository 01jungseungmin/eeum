package com.eeum.eeum.application.ai.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDraftPersistenceExecutorTest {

    @InjectMocks
    private AiDraftPersistenceExecutor executor;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;

    @Test
    void 초안_저장_시_DRAFT_메시지와_액션로그가_함께_저장된다() {
        // given
        Store store = mock(Store.class);
        Account owner = mock(Account.class);
        when(aiGeneratedMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiGeneratedMessage result = executor.saveDraftInTx(
                store, owner, AiMessageType.EVENT_MARKETING, "PRODUCT", 10L,
                "여름 이벤트", "시원한 여름 할인!", AiChannel.APP_PUSH,
                "AI_MESSAGE", "마케팅 문구 초안 생성");

        // then
        assertThat(result.getStatus()).isEqualTo(AiMessageStatus.DRAFT);
        assertThat(result.getType()).isEqualTo(AiMessageType.EVENT_MARKETING);
        assertThat(result.getTargetType()).isEqualTo("PRODUCT");
        assertThat(result.getTargetId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("여름 이벤트");
        assertThat(result.getContent()).isEqualTo("시원한 여름 할인!");
        assertThat(result.getOriginalContent()).isEqualTo("시원한 여름 할인!");
        assertThat(result.getChannel()).isEqualTo(AiChannel.APP_PUSH);

        ArgumentCaptor<AiActionLog> logCaptor = ArgumentCaptor.forClass(AiActionLog.class);
        verify(aiActionLogRepository).save(logCaptor.capture());
        AiActionLog actionLog = logCaptor.getValue();
        assertThat(actionLog.getActionType()).isEqualTo(AiActionType.DRAFT_CREATED);
        assertThat(actionLog.getTargetType()).isEqualTo("AI_MESSAGE");
        assertThat(actionLog.getDescription()).isEqualTo("마케팅 문구 초안 생성");

        verify(aiGeneratedMessageRepository).save(any(AiGeneratedMessage.class));
        verifyNoMoreInteractions(aiGeneratedMessageRepository, aiActionLogRepository);
    }
}
