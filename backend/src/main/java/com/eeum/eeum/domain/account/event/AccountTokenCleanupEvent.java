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

    public static AccountTokenCleanupEvent reAuthAndRefresh(Long accountId) {
        return new AccountTokenCleanupEvent(accountId, true, true, false, null);
    }

    public static AccountTokenCleanupEvent passwordResetAndRefresh(Long accountId) {
        return new AccountTokenCleanupEvent(accountId, true, false, true, null);
    }

    public static AccountTokenCleanupEvent oauthTemp(String tempToken) {
        return new AccountTokenCleanupEvent(null, false, false, false, tempToken);
    }
}
