package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiChatMessage;
import com.eeum.eeum.domain.ai.enums.AiChatRole;
import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AiChatMessageRecorderTest {

    @InjectMocks
    private AiChatMessageRecorder recorder;

    @Mock
    private AiChatMessageRepository aiChatMessageRepository;

    @Test
    void 대화_기록_저장_시_가게_계정_역할_내용이_그대로_저장된다() {
        // given
        Store store = mock(Store.class);
        Account account = mock(Account.class);

        // when
        recorder.record(store, account, AiChatRole.USER, "이번 달 매출 알려줘");

        // then
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(aiChatMessageRepository).save(captor.capture());
        AiChatMessage saved = captor.getValue();
        assertThat(saved.getStore()).isSameAs(store);
        assertThat(saved.getOwnerAccount()).isSameAs(account);
        assertThat(saved.getRole()).isEqualTo(AiChatRole.USER);
        assertThat(saved.getContent()).isEqualTo("이번 달 매출 알려줘");
        verifyNoMoreInteractions(aiChatMessageRepository);
    }
}
