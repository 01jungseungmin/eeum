package com.eeum.eeum.common.lock;

public final class RateLimitKeys {

    private RateLimitKeys() {
    }

    public static String emailVerification(String email) {
        return "rate-limit:email-verification:" + normalize(email);
    }

    public static String passwordReset(String email) {
        return "rate-limit:password-reset:" + normalize(email);
    }

    public static String loginFail(String email) {
        return "rate-limit:login-fail:" + normalize(email);
    }

    public static String fcmTest(Long ownerId) {
        return "rate-limit:fcm-test:" + ownerId;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
