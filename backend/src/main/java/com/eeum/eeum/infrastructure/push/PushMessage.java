package com.eeum.eeum.infrastructure.push;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

//FCM 푸시 발송 요청 모델 FcmPushAdapter에서 FCM API 형식으로 변환

@Getter
@Builder
public class PushMessage {

    // 수신자 FCM 토큰
    private String fcmToken;

    //푸시 알림 제목
    private String title;

    //푸시 알림 본문
    private String body;

    //클라이언트 딥링크 URL 예: /stores/3, /orders/42
    private String linkUrl;

    //추가 데이터 페이로드 (data message) 예: {"refType":"ORDER", "refId":"42"}
    private Map<String, String> data;
}
