package com.eeum.eeum.domain.account.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 세대 판정.
 *
 * <p>시각이 아니라 정수인 이유를 고정한다 — 발급 시점에 읽은 값이 낡았으면 그 토큰이 낡은 것이다.
 * 시각 비교는 초 단위 정밀도·timezone·"제재 직후 발급"에서 모두 어긋났다.
 */
class AccountTokenInvalidationTest {

    private Account account() {
        return Account.createUser("a@test.com", "pw", "이름", "닉", "010-0000-0000");
    }

    @Test
    void 신규_계정의_세대는_0이고_그_토큰은_유효하다() {
        Account account = account();

        assertThat(account.getTokenVersion()).isZero();
        assertThat(account.isTokenVersionCurrent(0L)).isTrue();
    }

    @Test
    void 무효화하면_이전_세대의_토큰이_거부된다() {
        // Given
        Account account = account();
        Long issuedVersion = account.getTokenVersion();

        // When
        account.invalidateIssuedTokens();

        // Then
        assertThat(account.isTokenVersionCurrent(issuedVersion)).isFalse();
    }

    @Test
    void 무효화_이후_발급된_토큰은_유효하다() {
        // Given: 제재 후 다시 로그인한 경우다 — 새 토큰까지 막으면 재활성화 후 로그인이 안 된다
        Account account = account();
        account.invalidateIssuedTokens();

        // When & Then
        assertThat(account.isTokenVersionCurrent(account.getTokenVersion())).isTrue();
    }

    @Test
    void 세대_claim이_없는_토큰은_무효다() {
        // Given: 이 기능 도입 전에 발급된 토큰이다.
        //        살려두면 배포 전 발급 토큰이 그대로 통과하므로 한 번 재로그인시킨다.

        // When & Then
        assertThat(account().isTokenVersionCurrent(null)).isFalse();
    }

    @Test
    void 무효화는_누적된다() {
        // Given: 제재 → 재활성화 → 재제재에서 각 시점의 토큰이 모두 막혀야 한다
        Account account = account();
        Long first = account.getTokenVersion();
        account.invalidateIssuedTokens();
        Long second = account.getTokenVersion();

        // When
        account.invalidateIssuedTokens();

        // Then
        assertThat(account.isTokenVersionCurrent(first)).isFalse();
        assertThat(account.isTokenVersionCurrent(second)).isFalse();
        assertThat(account.isTokenVersionCurrent(account.getTokenVersion())).isTrue();
    }
}
