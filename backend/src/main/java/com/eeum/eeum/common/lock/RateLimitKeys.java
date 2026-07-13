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

    // 이메일 인증 코드 검증 실패 카운터 — 6자리 코드 브루트포스 방지
    public static String emailCodeVerifyFail(String email) {
        return "rate-limit:email-code-verify-fail:" + normalize(email);
    }

    // 비밀번호 재설정 코드 검증 실패 카운터 — 6자리 코드 브루트포스 방지
    public static String passwordResetVerifyFail(String email) {
        return "rate-limit:password-reset-verify-fail:" + normalize(email);
    }

    public static String fcmTest(Long ownerId) {
        return "rate-limit:fcm-test:" + ownerId;
    }

    // 생활권 매칭 노출 가게 목록 조회(공개 API) — viewerKey(account:{id} 또는 ip:{ip}) 단위 과호출 방지
    public static String aiExposureView(String viewerKey) {
        return "rate-limit:ai-exposure-view:" + viewerKey;
    }

    // 생활권 매칭 노출 클릭 로그(공개 API) — viewerKey 단위 과호출 방지
    public static String aiExposureClick(String viewerKey) {
        return "rate-limit:ai-exposure-click:" + viewerKey;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
