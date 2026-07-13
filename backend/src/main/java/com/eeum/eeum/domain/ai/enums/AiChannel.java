package com.eeum.eeum.domain.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiChannel {
    APP_PUSH("앱 푸시", true),
    KAKAO_ALERT("카카오 알림톡", true),
    STORE_NOTICE("상점 공지", true),
    SNS_CARD("SNS 카드", false); // 문구 생성용 — 실제 공지 발송 채널에서는 제외

    private final String displayName;
    private final boolean noticeSendable;
}
