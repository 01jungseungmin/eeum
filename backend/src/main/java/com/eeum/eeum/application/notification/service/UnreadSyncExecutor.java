package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 커밋 후 unread를 비동기로 재계산해 요청 스레드의 커넥션과 응답 시간을 보호한다. */
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
