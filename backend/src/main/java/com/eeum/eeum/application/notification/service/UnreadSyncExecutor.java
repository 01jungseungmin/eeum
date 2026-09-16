package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 커밋 이후의 unread 재계산과 배지 전송을 요청 스레드에서 떼어낸다.
 *
 * afterCommit은 커밋을 수행한 그 스레드에서 돌고 바깥 커넥션은 아직 반납 전이라,
 * 콜백 안에서 DB를 읽으면 한 요청이 커넥션 2개를 동시에 쥔다.
 * 폐기돼도 안전하다 — 호출부가 제출 직전에 캐시를 무효화해 다음 조회가 DB에서 복구한다.
 * 실패를 삼키는 이유: 본 트랜잭션은 이미 커밋됐고 되돌릴 수 없다.
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
