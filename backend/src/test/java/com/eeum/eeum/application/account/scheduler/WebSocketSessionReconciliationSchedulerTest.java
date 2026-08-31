package com.eeum.eeum.application.account.scheduler;

import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountAuthState;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.security.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 세션 대조 백스톱.
 *
 * <p>정상 경로(제재 시 Redis 중계)가 유실돼도 이 대조가 결국 끊는다.
 * 유실 이유가 무엇이든(풀 폐기, Pub/Sub 유실, 인스턴스 재시작, 망 단절) 동일하게 복구된다.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionReconciliationSchedulerTest {

    @InjectMocks WebSocketSessionReconciliationScheduler scheduler;

    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock AccountRepository accountRepository;

    @Test
    void 정지된_계정의_연결을_끊는다() {
        // Given: 제재 시 종료 신호가 유실된 상태를 가정한다
        when(sessionRegistry.connectedTokenVersions()).thenReturn(Map.of(1L, 0L));
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.SUSPENDED, 0L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeAll(1L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void 계정은_활성이어도_토큰_세대가_낡았으면_끊는다() {
        // Given: 비밀번호 재설정·사장 승인은 계정을 ACTIVE로 남긴다.
        //        상태만 비교하면 회수된 토큰으로 연결된 세션이 그대로 살아남는다.
        when(sessionRegistry.connectedTokenVersions()).thenReturn(Map.of(1L, 3L));
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 4L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeAll(1L, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void 정상_연결은_건드리지_않는다() {
        // Given
        when(sessionRegistry.connectedTokenVersions()).thenReturn(Map.of(1L, 2L));
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 2L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry, never()).closeAll(anyLong(), any());
    }

    @Test
    void 조회되지_않는_계정의_연결도_끊는다() {
        // Given: 계정이 사라졌으면 붙어 있을 이유가 없다
        when(sessionRegistry.connectedTokenVersions()).thenReturn(Map.of(99L, 0L));
        when(accountRepository.findAuthStates(anyCollection())).thenReturn(List.of());

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeAll(eq(99L), any());
    }

    @Test
    void 붙어_있는_연결이_없으면_조회하지_않는다() {
        // Given: 스케줄러 스레드는 기본 1개다 — 할 일이 없으면 쿼리도 돌리지 않는다
        when(sessionRegistry.connectedTokenVersions()).thenReturn(Map.of());

        // When
        scheduler.closeRevokedSessions();

        // Then
        verifyNoInteractions(accountRepository);
    }
}
