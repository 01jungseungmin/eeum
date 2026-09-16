package com.eeum.eeum.domain.notification.event;

import com.eeum.eeum.infrastructure.push.PushMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

//알림 저장 트랜잭션 커밋 후 FCM 푸시 발송을 트리거하는 도메인 이벤트
//ApplicationEventPublisher.publishEvent() → @TransactionalEventListener(AFTER_COMMIT) → NotificationPushEventListener.onPushEvent()

@Getter
@RequiredArgsConstructor
public class NotificationPushEvent {

    //발송할 푸시 메시지
    private final PushMessage pushMessage;

    //수신자 accountId (FCM 토큰 만료 처리 등에 사용)
    private final Long accountId;
}
