package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokenGenerationRegressionTest {
    private final JwtProvider jwt = new JwtProvider("01234567890123456789012345678901", 3600, 7200, 300, 300);

    @Test
    void 연속_발급은_모든_용도에서_고유하다() {
        var tokens = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            assertThat(tokens.add(jwt.generateAccessToken(1L, "ROLE_USER", 1L))).isTrue();
            assertThat(tokens.add(jwt.generateRefreshToken(1L, 1L))).isTrue();
            assertThat(tokens.add(jwt.generateReAuthToken(1L, 1L))).isTrue();
            assertThat(tokens.add(jwt.generatePasswordResetToken(1L, 1L))).isTrue();
        }
    }

    @Test
    void 지연된_정리는_현재_세대_토큰을_지우지_않는다() {
        RedisUtil redis = mock(RedisUtil.class);
        TokenService service = new TokenService(jwt, redis);
        when(redis.get("refresh:1")).thenReturn(Optional.of(jwt.generateRefreshToken(1L, 2L)));
        when(redis.get("reauth:1")).thenReturn(Optional.of(jwt.generateReAuthToken(1L, 2L)));
        when(redis.get("password-reset:1")).thenReturn(Optional.of(jwt.generatePasswordResetToken(1L, 2L)));
        service.deleteRevokedTokens(1L, 2L, true, true, true);
        verify(redis, never()).compareAndDelete(anyString(), anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void 이전_세대는_조회한_토큰과_일치할_때만_삭제한다() {
        RedisUtil redis = mock(RedisUtil.class);
        TokenService service = new TokenService(jwt, redis);
        String old = jwt.generateRefreshToken(1L, 1L);
        when(redis.get("refresh:1")).thenReturn(Optional.of(old));
        service.deleteRevokedTokens(1L, 2L, true, false, false);
        verify(redis).compareAndDelete("refresh:1", old);
        verify(redis, never()).delete(anyString());
    }
}
