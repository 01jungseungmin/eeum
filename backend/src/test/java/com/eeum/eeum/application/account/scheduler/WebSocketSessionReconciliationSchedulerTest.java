package com.eeum.eeum.application.account.scheduler;

import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountAuthState;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
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

    private static final long NOT_EXPIRED = Long.MAX_VALUE;

    @InjectMocks WebSocketSessionReconciliationScheduler scheduler;

    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock SseEmitterManager sseEmitterManager;
    @Mock AccountRepository accountRepository;
    @Mock TokenService tokenService;

    @Test
    void 정지된_계정의_연결을_끊는다() {
        // Given: 제재 시 종료 신호가 유실된 상태를 가정한다
        WebSocketSessionRegistry.ConnectionCredentials credentials = credentials(1L, 0L, "active");
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of("s1", credentials));
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.SUSPENDED, 0L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeIfCurrent(
                "s1", credentials, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void 계정은_활성이어도_토큰_세대가_낡았으면_끊는다() {
        // Given: 비밀번호 재설정·사장 승인은 계정을 ACTIVE로 남긴다.
        //        상태만 비교하면 회수된 토큰으로 연결된 세션이 그대로 살아남는다.
        WebSocketSessionRegistry.ConnectionCredentials credentials = credentials(1L, 3L, "old");
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of("s1", credentials));
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 4L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeIfCurrent(
                "s1", credentials, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void 정상_연결은_건드리지_않는다() {
        // Given
        WebSocketSessionRegistry.ConnectionCredentials credentials = credentials(1L, 2L, "active");
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of("s1", credentials));
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 2L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry, never()).closeIfCurrent(any(), any(), any());
    }

    @Test
    void 조회되지_않는_계정의_연결도_끊는다() {
        // Given: 계정이 사라졌으면 붙어 있을 이유가 없다
        WebSocketSessionRegistry.ConnectionCredentials credentials = credentials(99L, 0L, "missing");
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of("s1", credentials));
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());
        when(accountRepository.findAuthStates(anyCollection())).thenReturn(List.of());

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeIfCurrent(eq("s1"), eq(credentials), any());
    }

    @Test
    void 블랙리스트된_토큰으로_연_SSE_연결을_끊는다() {
        // Given
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of());
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of(
                1L, new SseEmitterManager.ConnectionCredentials(2L, "revoked-fingerprint")));
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 2L)));
        when(tokenService.isFingerprintBlacklisted("revoked-fingerprint")).thenReturn(true);

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sseEmitterManager).closeIfCurrent(
                1L, new SseEmitterManager.ConnectionCredentials(2L, "revoked-fingerprint"));
    }

    @Test
    void 블랙리스트된_토큰으로_연_WebSocket_연결을_끊는다() {
        // Given: 로그아웃 종료 중계가 유실돼도 주기적 대조가 회수한다.
        WebSocketSessionRegistry.ConnectionCredentials credentials =
                credentials(1L, 2L, "revoked-fingerprint");
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of("s1", credentials));
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 2L)));
        when(tokenService.isFingerprintBlacklisted("revoked-fingerprint")).thenReturn(true);

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeIfCurrent(
                "s1", credentials, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
    }

    @Test
    void 구세대_WebSocket만_끊고_현재_세대_연결은_유지한다() {
        // Given: 새 연결이 생겨도 구세대 연결의 회수 정보가 세션별로 남아 있어야 한다.
        WebSocketSessionRegistry.ConnectionCredentials old = credentials(1L, 1L, "old");
        WebSocketSessionRegistry.ConnectionCredentials current = credentials(1L, 2L, "current");
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of("old", old, "current", current));
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());
        when(accountRepository.findAuthStates(anyCollection()))
                .thenReturn(List.of(new AccountAuthState(1L, AccountStatus.ACTIVE, 2L)));

        // When
        scheduler.closeRevokedSessions();

        // Then
        verify(sessionRegistry).closeIfCurrent(
                "old", old, WebSocketSessionRegistry.ACCOUNT_STATE_CHANGED);
        verify(sessionRegistry, never()).closeIfCurrent(eq("current"), eq(current), any());
    }

    @Test
    void 붙어_있는_연결이_없으면_조회하지_않는다() {
        // Given: 스케줄러 스레드는 기본 1개다 — 할 일이 없으면 쿼리도 돌리지 않는다
        when(sessionRegistry.connectedCredentials()).thenReturn(Map.of());
        when(sseEmitterManager.connectedCredentials()).thenReturn(Map.of());

        // When
        scheduler.closeRevokedSessions();

        // Then
        verifyNoInteractions(accountRepository);
    }

    private WebSocketSessionRegistry.ConnectionCredentials credentials(
            Long accountId, Long tokenVersion, String fingerprint) {
        return new WebSocketSessionRegistry.ConnectionCredentials(
                accountId, tokenVersion, fingerprint, NOT_EXPIRED);
    }
}
