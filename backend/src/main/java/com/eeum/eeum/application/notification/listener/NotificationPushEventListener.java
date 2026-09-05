package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.domain.notification.event.NotificationPushEvent;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 알림 저장 트랜잭션 커밋 이후 비동기로 FCM 푸시를 발송
// - AFTER_COMMIT: 트랜잭션이 완전히 커밋된 뒤에만 실행 — DB 저장 실패 시 푸시 미발송
// - @Async: 별도 스레드에서 실행 — 메인 요청 응답 지연 없음
// - 전용 풀: FCM은 외부 HTTP다. 일반 풀과 공유하면 포화 시 CallerRuns로
//   요청 스레드가 FCM 응답을 기다리게 된다(AsyncConfig 참고).
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushEventListener {

    private final PushAdapter pushAdapter;

    @Async("notificationPushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 현재 트랜잭션이 성공적으로 커밋된 뒤에만 이벤트를 처리
    public void onPushEvent(NotificationPushEvent event) {
        PushResult result = pushAdapter.send(event.getPushMessage());

        if (result.isSuccess()) {
            log.debug("FCM 푸시 발송 완료: accountId={}, messageId={}",
                    event.getAccountId(), result.getMessageId());
        } else {
            log.warn("FCM 푸시 발송 실패: accountId={}, errorCode={}",
                    event.getAccountId(), result.getErrorCode());
        }
    }
}
