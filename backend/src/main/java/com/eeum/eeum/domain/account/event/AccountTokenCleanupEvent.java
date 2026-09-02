package com.eeum.eeum.domain.account.event;

public record AccountTokenCleanupEvent(
        Long accountId,
        boolean deleteRefreshToken,
        boolean consumeReAuthToken,
        boolean deletePasswordResetToken,
        String oauthTempToken
) {
    public static AccountTokenCleanupEvent refreshOnly(Long accountId) {
        return new AccountTokenCleanupEvent(accountId, true, false, false, null);
    }

    /**
     * 계정 제재(정지·강제 탈퇴) 시 남은 토큰을 전부 회수한다.
     *
     * <p>Refresh만 지우면 재인증·비밀번호 재설정 토큰이 TTL 동안 살아남는다.
     * Access Token은 여기서 회수할 수 없다 — 블랙리스트는 토큰 문자열로 키를 잡는데
     * 관리자는 대상자의 토큰을 갖고 있지 않다. 대신 REST는 요청마다, WebSocket은
     * CONNECT와 발행마다 계정 상태를 다시 확인해 막는다.
     */
    public static AccountTokenCleanupEvent allTokens(Long accountId) {
        return new AccountTokenCleanupEvent(accountId, true, true, true, null);
    }

    public static AccountTokenCleanupEvent oauthTemp(String tempToken) {
        return new AccountTokenCleanupEvent(null, false, false, false, tempToken);
    }
}
