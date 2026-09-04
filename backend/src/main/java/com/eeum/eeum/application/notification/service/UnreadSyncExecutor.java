package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 커밋 이후의 unread 재계산과 실시간 배지 전송을 요청 스레드에서 떼어낸다.
 *
 * <p>{@code afterCommit}은 별도 스레드가 아니다 — 커밋을 수행한 그 스레드(대개 Tomcat 요청
 * 스레드)에서 실행되고, 그 시점 바깥 트랜잭션의 커넥션은 <b>아직 반납되지 않았다</b>
 * (반납은 {@code afterCompletion} 이후). 그래서 콜백 안에서 DB를 다시 읽으면 한 요청이
 * 커넥션 2개를 동시에 쥔다. 여기로 넘기면 재계산은 async 스레드의 트랜잭션 하나로 끝나고
 * 요청 스레드는 원래 커넥션만 반납하고 나간다.
 *
 * <p>이 작업은 <b>폐기돼도 안전하다.</b> 호출부가 async 제출 직전에 캐시를 무효화하므로,
 * 큐 포화로 버려지면 캐시가 미스 상태로 남아 다음 조회가 DB에서 정확한 값을 복구한다.
 * 잃는 것은 실시간 배지 갱신 한 번이고, 사용자가 화면을 다시 열면 맞는 값을 본다.
 *
 * <p>실패를 삼키는 이유: 이 시점에는 본 트랜잭션이 이미 커밋됐다. 재계산이 안 됐다고
 * 성공한 주문·읽음 처리를 되돌릴 수는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadSyncExecutor {

    private final UnreadCountService unreadCountService;
    private final RealtimeRelayPublisher realtimeRelayPublisher;

    @Async("asyncTaskExecutor")
    public void rebuildAndPush(Long accountId, long generation) {
        rebuildAndPushOne(accountId, generation);
    }

    // 참여자 전원을 한 작업으로 처리한다 — 인원수만큼 async 작업을 제출하면
    // 채팅방 하나 종료에 큐가 통째로 차고, 그 포화가 알림 생성 작업까지 밀어낸다.
    @Async("asyncTaskExecutor")
    public void rebuildAndPushAll(Map<Long, Long> generations) {
        if (generations == null) return;
        generations.forEach(this::rebuildAndPushOne);
    }

    // refreshFromDb가 방금 만든 스냅샷을 그대로 싣는다.
    // 여기서 getUnreadCount를 다시 부르면 트랜잭션과 Redis 왕복이 한 번 더 생긴다.
    private void rebuildAndPushOne(Long accountId, long generation) {
        try {
            UnreadSnapshotRebuilder.RebuildResult result = unreadCountService.refreshFromDb(accountId, generation);
            if (result.applied()) {
                realtimeRelayPublisher.publishUnreadCount(accountId, result.snapshot());
            }
        } catch (RuntimeException e) {
            log.error("unread 재계산 실패 — 캐시는 무효화된 상태로 남는다: accountId={}", accountId, e);
        }
    }
}
