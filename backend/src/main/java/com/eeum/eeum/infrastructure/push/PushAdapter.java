package com.eeum.eeum.infrastructure.push;

import java.util.List;

//푸시 알림 발송 어댑터 인터페이스
public interface PushAdapter {

    // 단건 푸시 발송
    PushResult send(PushMessage message);

    //배치 푸시 발송 — FCM 멀티캐스트 API 활용 최대 500건/요청 (FCM 제한)
    List<PushResult> sendBatch(List<PushMessage> messages);
}