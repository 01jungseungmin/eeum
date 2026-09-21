package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLogoutServiceTest {
    @InjectMocks AccountLogoutService service;
    @Mock AccountRepository accounts;
    @Mock TokenService tokens;
    @Mock JwtProvider jwt;
    @Mock ApplicationEventPublisher publisher;

    @Test
    void 현재_세대의_로그아웃은_커밋과_함께_영속_회수를_요청한다() {
        Account account = Account.createUser("a@test.com", "pw", "name", "nick", "010");
        when(accounts.findByIdWithLock(1L)).thenReturn(Optional.of(account));
        when(jwt.getTokenVersion("access")).thenReturn(0L);
        when(jwt.getTokenVersion("refresh")).thenReturn(0L);
        when(tokens.validateRefreshToken("refresh")).thenReturn(1L);
        service.logout(1L, "access", "refresh");
        verify(publisher).publishEvent(AccountTokenCleanupEvent.allTokens(1L));
    }

    @Test
    void 이전_세대의_로그아웃으로_새_세션을_회수할_수_없다() {
        Account account = Account.createUser("a@test.com", "pw", "name", "nick", "010");
        account.invalidateIssuedTokens();
        when(accounts.findByIdWithLock(1L)).thenReturn(Optional.of(account));
        when(jwt.getTokenVersion("access")).thenReturn(0L);
        assertThatThrownBy(() -> service.logout(1L, "access", "refresh"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(tokens, publisher);
    }
}
