package com.eeum.eeum.application.auth.listener;

import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock StringRedisTemplate redisTemplate;
    @Mock RedisLockService redisLockService;

    // 락 획득은 이 테스트의 관심사가 아니다 — 잠근 뒤 실행된다고 보고 본문만 검증한다.
    // 실제 직렬화는 AccountTokenCleanupLockIntegrationTest가 실제 Redis로 확인한다.
    @BeforeEach
    void runInsideLock() {
        lenient().doAnswer(invocation -> {
            invocation.getArgument(3, Runnable.class).run();
            return null;
        }).when(redisLockService).executeWithLockWaiting(any(), any(), any(), any());
    }

    // ─────────────────── refreshOnly 이벤트 ───────────────────

    @Test
    void refreshOnly_이벤트_수신_시_deleteRefreshToken만_호출() {
        // given
        Long accountId = 1L;
        AccountTokenCleanupEvent event = AccountTokenCleanupEvent.refreshOnly(accountId);

        // when
        listener.onAccountTokenCleanup(event);

        // then
        verify(tokenService).deleteRefreshToken(accountId);
        verify(tokenService, never()).deleteReAuthToken(any());
        verify(tokenService, never()).deletePasswordResetToken(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    // ─────────────────── allTokens 이벤트 (계정 제재) ───────────────────

    @Test
    void allTokens_이벤트_수신_시_refresh_reauth_passwordReset_토큰을_모두_삭제() {
        // given: 정지·강제 탈퇴에서 Refresh만 지우면 재인증·비밀번호 재설정 토큰이 TTL 동안 살아남는다
        Long accountId = 2L;
        AccountTokenCleanupEvent event = AccountTokenCleanupEvent.allTokens(accountId);

        // when
        listener.onAccountTokenCleanup(event);

        // then
        verify(tokenService).deleteRefreshToken(accountId);
        verify(tokenService).deleteReAuthToken(accountId);
        verify(tokenService).deletePasswordResetToken(accountId);
        verify(redisTemplate, never()).delete(anyString());
    }

    // ─────────────────── oauthTemp 이벤트 ───────────────────

    @Test
    void oauthTemp_이벤트_수신_시_redis_oauth_temp_키_삭제() {
        // given
        String tempToken = "uuid-temp-token";
        AccountTokenCleanupEvent event = AccountTokenCleanupEvent.oauthTemp(tempToken);

        // when
        listener.onAccountTokenCleanup(event);

        // then: "oauth:temp:{tempToken}" 키 삭제, tokenService 호출 없음
        verify(redisTemplate).delete("oauth:temp:" + tempToken);
        verify(tokenService, never()).deleteRefreshToken(any());
        verify(tokenService, never()).deleteReAuthToken(any());
        verify(tokenService, never()).deletePasswordResetToken(any());
    }

    // ─────────────────── Redis 예외 발생 시 외부 전파 없음 ───────────────────

    @Test
    void Redis_예외_발생_시_예외를_외부로_전파하지_않고_로그만_기록() {
        // given
        Long accountId = 1L;
        AccountTokenCleanupEvent event = AccountTokenCleanupEvent.refreshOnly(accountId);
        doThrow(new RuntimeException("Redis 연결 실패")).when(tokenService).deleteRefreshToken(accountId);

        // when: 예외가 전파되지 않아야 함
        listener.onAccountTokenCleanup(event);

        // then: 예외 미전파 확인
        verify(tokenService).deleteRefreshToken(accountId);
    }
}
