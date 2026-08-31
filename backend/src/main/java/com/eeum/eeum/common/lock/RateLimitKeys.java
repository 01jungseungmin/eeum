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

    // 운영 실패 관리자 알림 스로틀 — 분류 단위.
    // PortOne 장애처럼 한 원인으로 실패가 연속 발생할 때 관리자 전원에게 건별 알림이 나가면
    // 알림 폭탄이 되고 정작 다른 분류의 실패가 묻힌다. 분류별로 쿨다운을 둔다.
    public static String operationFailureAlert(String category) {
        return "rate-limit:operation-failure-alert:" + (category == null ? "UNKNOWN" : category);
    }

    // Webhook 서명 검증 실패 누적 카운터 — 인증 없는 엔드포인트라 건별 DB 기록 대신 집계한다.
    public static String webhookSignatureFailureCount() {
        return "rate-limit:webhook-signature-fail:count";
    }

    // Webhook 서명 검증 실패의 DB 이력 기록 쿨다운 — 구간당 1건만 남긴다.
    public static String webhookSignatureFailureRecord() {
        return "rate-limit:webhook-signature-fail:record";
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
