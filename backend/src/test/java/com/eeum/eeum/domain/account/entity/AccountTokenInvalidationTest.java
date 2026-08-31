package com.eeum.eeum.domain.account.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 무효화 시각 판정.
 *
 * <p>JWT의 {@code iat}는 초 단위다. 같은 초에 발급과 무효화가 겹치면 순서를 알 수 없는데,
 * 그때 살려두면 제재 직전에 발급된 토큰이 통과한다. 막는 쪽을 택한 것이 의도임을 고정한다.
 */
class AccountTokenInvalidationTest {

    private Account account() {
        return Account.createUser("a@test.com", "pw", "이름", "닉", "010-0000-0000");
    }

    private Instant toInstant(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }

    @Test
    void 무효화_이력이_없으면_어떤_토큰도_유효하다() {
        assertThat(account().isTokenInvalidated(Instant.now())).isFalse();
    }

    @Test
    void 무효화_시각보다_먼저_발급된_토큰은_무효다() {
        // Given
        LocalDateTime invalidatedAt = LocalDateTime.now();
        Account account = account();
        account.invalidateTokensBefore(invalidatedAt);

        // When & Then
        assertThat(account.isTokenInvalidated(toInstant(invalidatedAt.minusMinutes(1)))).isTrue();
    }

    @Test
    void 무효화_시각_이후에_발급된_토큰은_유효하다() {
        // Given: 제재 후 다시 로그인한 경우다 — 새 토큰까지 막으면 재활성화 후 로그인이 안 된다
        LocalDateTime invalidatedAt = LocalDateTime.now();
        Account account = account();
        account.invalidateTokensBefore(invalidatedAt);

        // When & Then
        assertThat(account.isTokenInvalidated(toInstant(invalidatedAt.plusSeconds(2)))).isFalse();
    }

    @Test
    void 같은_초에_발급된_토큰은_무효로_본다() {
        // Given: iat가 초 단위라 순서를 구분할 수 없다.
        //        살려두면 제재 직전 발급 토큰이 통과하므로 최대 1초 과하게 막는다.
        LocalDateTime invalidatedAt = LocalDateTime.now().withNano(500_000_000);
        Account account = account();
        account.invalidateTokensBefore(invalidatedAt);

        // When & Then
        assertThat(account.isTokenInvalidated(toInstant(invalidatedAt.withNano(900_000_000))))
                .isTrue();
    }
}
