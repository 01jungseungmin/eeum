package com.eeum.eeum.application.chat.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.application.chat.ChatRedisKeys;
import com.eeum.eeum.application.chat.service.ChatMessageService;
import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 채팅 unread 캐시 ↔ DB 정합성 보정.
 * <p>
 * 알림 도메인의 NotificationCleanupScheduler.recalculateUnreadCounts가 알림함 배지를 보정하는 것과 같은 역할을
 * 채팅 전용 배지(unread:chat:*)에 대해 수행한다. 채팅 쪽에는 그동안 보정 장치가 없어 한 번 어긋난 값이
 * 영구히 남았다.
 * <p>
 * 보정 대상:
 * <ol>
 *   <li>종료된 방의 방별 키 — 사용자가 읽어서 회수할 수 없으므로 삭제</li>
 *   <li>전체 키 — DB 기준값(활성 방 한정)으로 재설정</li>
 * </ol>
 * 키가 많을 수 있으므로 KEYS 대신 SCAN으로 순회한다 (KEYS는 Redis를 블로킹한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUnreadReconcileScheduler {

    private static final String TOTAL_KEY_PREFIX = "unread:chat:";
    private static final String ROOM_KEY_MARKER = ":room:";
    private static final int SCAN_BATCH = 500;

    private final StringRedisTemplate redisTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageService chatMessageService;
    private final ChatUnreadService chatUnreadService;

    // 5분 주기 — 알림 unread 보정(NotificationCleanupScheduler)과 동일 주기
    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = "reconcileChatUnread", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    @Transactional(readOnly = true)
    public void reconcileChatUnread() {
        List<String> totalKeys = new ArrayList<>();
        List<String> roomKeys = new ArrayList<>();

        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(TOTAL_KEY_PREFIX + "*").count(SCAN_BATCH).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (key.contains(ROOM_KEY_MARKER)) {
                    roomKeys.add(key);
                } else {
                    totalKeys.add(key);
                }
            }
        } catch (Exception e) {
            log.warn("[ChatUnreadReconcile] 키 스캔 실패: {}", e.getMessage());
            return;
        }

        int removedRoomKeys = removeClosedRoomKeys(roomKeys);
        int correctedTotals = correctTotals(totalKeys);

        if (removedRoomKeys > 0 || correctedTotals > 0) {
            log.info("[ChatUnreadReconcile] 보정 완료: 종료방 키 삭제={}, 전체 카운트 보정={}",
                    removedRoomKeys, correctedTotals);
        }
    }

    // 종료된 방의 방별 unread 키 삭제
    private int removeClosedRoomKeys(List<String> roomKeys) {
        if (roomKeys.isEmpty()) {
            return 0;
        }
        Set<Long> roomIds = new HashSet<>();
        for (String key : roomKeys) {
            parseRoomId(key).ifPresent(roomIds::add);
        }
        if (roomIds.isEmpty()) {
            return 0;
        }

        Set<Long> closedRoomIds = new HashSet<>(
                chatRoomRepository.findClosedRoomIdsIn(new ArrayList<>(roomIds)));
        if (closedRoomIds.isEmpty()) {
            return 0;
        }

        List<String> toDelete = roomKeys.stream()
                .filter(key -> parseRoomId(key).map(closedRoomIds::contains).orElse(false))
                .toList();
        if (toDelete.isEmpty()) {
            return 0;
        }
        redisTemplate.delete(toDelete);
        return toDelete.size();
    }

    // 전체 unread 키를 DB 기준값으로 재설정
    private int correctTotals(List<String> totalKeys) {
        int corrected = 0;
        for (String key : totalKeys) {
            try {
                Long accountId = Long.parseLong(key.substring(TOTAL_KEY_PREFIX.length()));

                // 보정 기준값을 먼저 읽고, DB 조회 후 CAS로 덮어쓴다.
                // 무조건 SET하면 "DB 조회 → SET" 사이에 도착한 메시지의 increment가 사라진다.
                String cached = redisTemplate.opsForValue().get(key);
                Long expected = cached != null ? Long.parseLong(cached) : null;
                long dbCount = chatMessageService.sumUnreadFromDb(accountId);

                if (expected != null && expected == dbCount) {
                    continue; // 이미 일치
                }
                if (chatUnreadService.compareAndSetTotal(accountId, expected, dbCount)) {
                    corrected++;
                    log.debug("[ChatUnreadReconcile] 전체 카운트 보정: accountId={}, redis={}, db={}",
                            accountId, expected, dbCount);
                } else {
                    // 조회 사이에 값이 변했다 — 새 증가분을 덮지 않고 다음 주기에 다시 맞춘다
                    log.debug("[ChatUnreadReconcile] 보정 중 값 변경으로 건너뜀: accountId={}", accountId);
                }
            } catch (NumberFormatException e) {
                log.warn("[ChatUnreadReconcile] 키 형식 오류로 건너뜀: key={}", key);
            } catch (Exception e) {
                log.warn("[ChatUnreadReconcile] 보정 실패: key={}, error={}", key, e.getMessage());
            }
        }
        return corrected;
    }

    // unread:chat:{accountId}:room:{roomId} → roomId
    private java.util.Optional<Long> parseRoomId(String key) {
        int idx = key.indexOf(ROOM_KEY_MARKER);
        if (idx < 0) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(
                    Long.parseLong(key.substring(idx + ROOM_KEY_MARKER.length())));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
