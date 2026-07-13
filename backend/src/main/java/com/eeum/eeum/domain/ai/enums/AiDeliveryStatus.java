package com.eeum.eeum.domain.ai.enums;

public enum AiDeliveryStatus {
    SENT,                // 발송 성공
    FAILED,              // 발송 실패
    SKIPPED_NO_TOKEN,    // FCM 토큰 없음 — 스킵
    SKIPPED_NO_CONSENT,  // 마케팅 수신 동의 없음 — 제외
    SKIPPED_DND,         // 방해 금지(DND) 시간대 — 푸시만 스킵 (인앱 알림함에는 남음)
    NO_TARGET            // 발송 대상 0명 (메시지 단위 기록)
}
