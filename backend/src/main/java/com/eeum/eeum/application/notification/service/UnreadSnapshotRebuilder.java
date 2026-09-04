package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * unread 스냅샷을 DB 기준으로 다시 계산해 Redis 두 키를 원자적으로 교체한다.
 *
 * <p><b>{@link UnreadCountService}에서 분리한 이유는 트랜잭션 경계다.</b> 이 프로젝트에서
 * 트랜잭션이 필요한 곳은 여기뿐이다 — 계정 행 비관적 락과 count 조회. 캐시 히트만으로
 * 끝나는 조회까지 같은 애너테이션 아래 두면 Redis만 읽는 요청이 EntityManager를 열게 되고,
 * 그 메서드에 SELECT가 한 줄이라도 추가되는 순간 커넥션을 메서드 끝까지 물게 된다.
 * 같은 클래스의 private 메서드로 두면 프록시를 거치지 않아 {@code @Transactional}이
 * 조용히 무시되므로, 경계를 내리려면 빈을 나누는 수밖에 없다.
 *
 * <p>전파는 {@code REQUIRED}다. 예전에는 {@code REQUIRES_NEW}였는데, 호출부가
 * {@code afterCommit} 콜백이라 <b>아직 반납되지 않은 바깥 커넥션 위에 두 번째 커넥션을</b>
 * 잡았다. 풀이 10이면 그런 요청은 동시 5개가 상한이고 10개가 겹치면 전원이 두 번째
 * 커넥션을 기다리는 데드락이 된다(자원 예산 문서 R2). 지금은 호출부가
 * {@link UnreadSyncExecutor}의 {@code @Async} 스레드라 바깥 트랜잭션 자체가 없다.
 *
 * <p>계정 행 락을 mutex로 쓰는 이유: 재계산은 "DB를 읽어 Redis에 통째로 덮어쓰기"라
 * 두 재계산이 겹치면 나중에 읽은 쪽이 먼저 쓰고 먼저 읽은 쪽이 나중에 써서 낡은 값이 남는다.
 * 읽기와 쓰기를 하나의 mutex 안에 묶어야 마지막 기록이 항상 최신 DB 상태가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadSnapshotRebuilder {

    // 전체 키를 SET하고 카테고리 해시를 통째로 갈아끼운다.
    // 두 키가 서로 다른 스냅샷을 가리키는 중간 상태를 남기지 않기 위해 한 스크립트로 묶었다.
    private static final DefaultRedisScript<Long> REPLACE_UNREAD_SNAPSHOT = new DefaultRedisScript<>(
            "redis.call('set', KEYS[1], ARGV[1]) "
                    + "redis.call('del', KEYS[2]) "
                    + "for i = 2, #ARGV, 2 do redis.call('hset', KEYS[2], ARGV[i], ARGV[i + 1]) end "
                    + "return 1",
            Long.class);

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public UnreadCountResponseDto rebuild(Long accountId) {
        accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        long dbCount = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
        Map<NotificationCategory, Long> categoryCounts = emptyCategoryCounts();
        categoryCounts.putAll(notificationRepository.countUnreadByCategory(accountId));

        List<String> snapshotArgs = new ArrayList<>();
        snapshotArgs.add(String.valueOf(dbCount));
        categoryCounts.forEach((category, count) -> {
            snapshotArgs.add(category.name());
            snapshotArgs.add(String.valueOf(count));
        });
        redisTemplate.execute(
                REPLACE_UNREAD_SNAPSHOT,
                List.of(UnreadCacheKeys.total(accountId), UnreadCacheKeys.category(accountId)),
                snapshotArgs.toArray());

        log.debug("unread 캐시 동기화: accountId={}, count={}", accountId, dbCount);
        return UnreadCountResponseDto.of(dbCount, categoryCounts);
    }

    // 카테고리가 하나라도 빠지면 조회가 그 캐시를 미완성으로 보고 DB로 되돌아간다.
    // DB에 해당 카테고리 알림이 없어도 0으로 채워 넣는 이유다.
    private Map<NotificationCategory, Long> emptyCategoryCounts() {
        Map<NotificationCategory, Long> counts = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            counts.put(category, 0L);
        }
        return counts;
    }
}
