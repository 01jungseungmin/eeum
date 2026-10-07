package com.eeum.eeum.application.auth.listener;

import com.eeum.eeum.application.auth.service.AuthAccountReader;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountTokenCleanupEventListenerTest {
    @InjectMocks AccountTokenCleanupEventListener listener;
    @Mock TokenService tokenService;
    @Mock AuthAccountReader authAccountReader;
    @Mock StringRedisTemplate redisTemplate;

    private void generation() {
        Account account = Account.createUser("a@test.com", "pw", "name", "nick", "010");
        account.invalidateIssuedTokens();
        when(authAccountReader.byId(1L)).thenReturn(account);
    }

    @Test
    void refreshOnly는_현재_세대보다_오래된_refresh만_정리한다() {
        generation();
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.refreshOnly(1L));
        verify(tokenService).deleteRevokedTokens(1L, 1L, true, false, false);
    }

    @Test
    void allTokens는_세가지_토큰의_이전_세대만_정리한다() {
        generation();
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(1L));
        verify(tokenService).deleteRevokedTokens(1L, 1L, true, true, true);
    }

    @Test
    void oauthTemp는_계정_조회없이_소비한다() {
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.oauthTemp("temp"));
        verify(redisTemplate).delete("oauth:temp:temp");
        verifyNoInteractions(authAccountReader, tokenService);
    }

    @Test
    void Redis_실패는_이미_커밋된_계정변경을_실패시키지_않는다() {
        generation();
        doThrow(new IllegalStateException("redis unavailable")).when(tokenService)
                .deleteRevokedTokens(1L, 1L, true, true, true);
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(1L));
        verify(tokenService).deleteRevokedTokens(1L, 1L, true, true, true);
    }
}
