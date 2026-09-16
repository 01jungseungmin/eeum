package com.eeum.eeum.infrastructure.push;

import lombok.Builder;
import lombok.Getter;

//FCM 푸시 발송 결과
@Getter
@Builder
public class PushResult {

    private final boolean success;

    //FCM messageId (성공 시)
    private final String messageId;

    //실패 사유 (실패 시)
    private final String errorCode;

    //만료/무효 토큰 여부 — true이면 Account.fcmToken을 무효화해야 함
    private final boolean invalidToken;

    public static PushResult success(String messageId) {
        return PushResult.builder()
                .success(true)
                .messageId(messageId)
                .invalidToken(false)
                .build();
    }

    public static PushResult failure(String errorCode, boolean invalidToken) {
        return PushResult.builder()
                .success(false)
                .errorCode(errorCode)
                .invalidToken(invalidToken)
                .build();
    }
}
