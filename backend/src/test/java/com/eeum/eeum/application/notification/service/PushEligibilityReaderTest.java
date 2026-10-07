package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.listener.NotificationPushEventListener;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.event.NotificationPushEvent;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushMessage;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;

class PushEligibilityReaderTest {
    @Test
    void 큐에_적재한_후_탈퇴하면_발송하지_않는다() {
        AccountRepository repository = mock(AccountRepository.class);
        PushAdapter adapter = mock(PushAdapter.class);
        Account account = Account.createUser("a@test.com", "pw", "name", "nick", "010");
        account.updateFcmToken("old-token");
        var event = new NotificationPushEvent(PushMessage.builder().fcmToken("old-token").build(), 1L);
        account.withdraw();
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        new NotificationPushEventListener(adapter, new PushEligibilityReader(repository)).onPushEvent(event);
        verifyNoInteractions(adapter);
    }

    @Test
    void 토큰이_교체되면_이전_기기로_발송하지_않는다() {
        AccountRepository repository = mock(AccountRepository.class);
        PushAdapter adapter = mock(PushAdapter.class);
        Account account = Account.createUser("a@test.com", "pw", "name", "nick", "010");
        account.updateFcmToken("new-token");
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        new NotificationPushEventListener(adapter, new PushEligibilityReader(repository)).onPushEvent(
                new NotificationPushEvent(PushMessage.builder().fcmToken("old-token").build(), 1L));
        verifyNoInteractions(adapter);
    }
}
