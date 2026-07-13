package com.eeum.eeum.infrastructure.alimtalk;

// 알림톡 발송 요청 모델 — phone 원문은 로그에 출력하지 않는다
public record AlimtalkMessage(
        String phone,
        String templateCode,
        String title,
        String content
) {
}
