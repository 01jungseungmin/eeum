package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.event.NotificationPushEvent;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.push.PushMessage;
import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // Redis 키 패턴: unread:account:{accountId}
    private static final String UNREAD_KEY_PREFIX = "unread:account:";

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final SseEmitterManager sseEmitterManager;

    // ===================== SSE 구독 =====================

    // 클라이언트(웹)가 SSE 구독을 요청할 때 호출
    // 연결 직후 현재 unread 카운트를 즉시 전송
    public SseEmitter subscribe(Long accountId) {
        SseEmitter emitter = sseEmitterManager.subscribe(accountId);
        // 구독 직후 현재 카운트를 즉시 전달 (페이지 진입 시 배지 즉시 표시)
        sseEmitterManager.sendUnreadCount(accountId, getUnreadCount(accountId));
        return emitter;
    }

    // ===================== 알림 생성 (다른 도메인 서비스에서 호출) =====================

    /**
     * 단건 알림 생성
     * 1. 수신 동의 여부 확인
     * 2. Notification 저장 + Redis unread INCR
     * 3. SSE로 웹 클라이언트에 unread 카운트 즉시 전달
     * 4. DND가 비활성 상태이고 FCM 토큰이 있으면 NotificationPushEvent 발행 (AFTER_COMMIT + @Async → FCM)
     */
    @Transactional
    public NotificationResponseDto createNotification(NotificationCreateRequestDto request) {
        return createNotification(request, true);
    }

    // FCM 이벤트 발행 없이 Notification만 생성 — 호출자가 이미 자체 채널(APP_PUSH/KAKAO_ALERT 등)로
    // 실제 발송을 처리하는 경우, FCM 중복 발송 없이 알림함/unread/SSE만 동일하게 반영하고 싶을 때 사용
    // (예: AiMessageDispatchService가 PushAdapter/AlimtalkAdapter로 직접 발송하면서 알림함 기록만 남기는 경우)
    @Transactional
    public NotificationResponseDto createNotificationWithoutPush(NotificationCreateRequestDto request) {
        return createNotification(request, false);
    }

    private NotificationResponseDto createNotification(NotificationCreateRequestDto request, boolean triggerPush) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 1. 수신 동의 여부 검증
        Optional<NotificationSettings> settingsOpt =
                settingsRepository.findByAccount_AccountId(account.getAccountId());

        boolean allowed = settingsOpt.map(s -> s.isAllowed(request.getType())).orElse(true);
        if (!allowed) {
            log.debug("알림 수신 거부: accountId={}, type={}", account.getAccountId(), request.getType());
            return null;
        }

        // 2. Notification 저장
        Notification notification = Notification.create(
                account,
                request.getType(),
                request.getTitle(),
                request.getContent(),
                request.getRefType(),
                request.getRefId(),
                request.getLinkUrl()
        );
        notificationRepository.save(notification);

        // 3~4. Redis unread 증가 + SSE 배지 갱신은 커밋 후에 수행한다 — 트랜잭션 중간에 하면
        // 이후 롤백(배치 발송 중 특정 계정 예외 등) 시 저장되지 않은 알림의 unread/배지가 남아 DB와 어긋난다.
        Long targetAccountId = account.getAccountId();
        runAfterCommit(() -> {
            incrementUnreadCount(targetAccountId);
            pushUnreadCount(targetAccountId);
        });

        // 5. FCM 푸시 이벤트 발행 (DND 비활성 + 토큰 있는 경우만) — 호출자가 자체 발송을 이미 처리하면 생략
        if (triggerPush) {
            boolean dndActive = settingsOpt.map(NotificationSettings::isDndActive).orElse(false);
            if (!dndActive && account.getFcmToken() != null) {
                publishPushEvent(account, request);
            } else if (dndActive) {
                log.debug("DND 활성 — 푸시 스킵: accountId={}", account.getAccountId());
            }
        }

        log.debug("알림 생성: accountId={}, type={}", account.getAccountId(), request.getType());
        return NotificationResponseDto.from(notification);
    }

    // 다수 사용자 일괄 알림 생성 (그룹 채팅, 시스템 공지 등) 거부된 사용자는 건너뛰고 나머지만 저장
    @Transactional
    public List<NotificationResponseDto> createNotificationsBatch(
            List<NotificationCreateRequestDto> requests
    ) {
        List<NotificationResponseDto> results = new ArrayList<>();
        for (NotificationCreateRequestDto request : requests) {
            NotificationResponseDto result = createNotification(request);
            if (result != null) results.add(result);
        }
        return results;
    }

    // ===================== 조회 =====================

    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getMyNotifications(Long accountId, Pageable pageable) {
        return notificationRepository
                .findAllByAccount_AccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(NotificationResponseDto::from);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getMyUnreadNotifications(Long accountId, Pageable pageable) {
        return notificationRepository
                .findAllByAccount_AccountIdAndIsReadFalseOrderByCreatedAtDesc(accountId, pageable)
                .map(NotificationResponseDto::from);
    }

    // 카테고리별 알림 조회 (웹 UI 탭 필터) category == null 이면 전체 조회
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getMyNotificationsByCategory(
            Long accountId,
            NotificationCategory category,
            Pageable pageable
    ) {
        if (category == null) {
            return getMyNotifications(accountId, pageable);
        }
        List<NotificationType> types = category.getTypes();
        return notificationRepository
                .findAllByAccountIdAndTypeIn(accountId, types, pageable)
                .map(NotificationResponseDto::from);
    }

    // 안 읽은 알림 수 — 전체는 Redis 우선 조회(캐시 미스 시 DB fallback 후 복구), 카테고리별은 DB 집계
    @Transactional(readOnly = true)
    public UnreadCountResponseDto getUnreadCount(Long accountId) {
        String key = UNREAD_KEY_PREFIX + accountId;
        String cached = redisTemplate.opsForValue().get(key);

        long count;
        if (cached != null) {
            count = Long.parseLong(cached);
        } else {
            count = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
            redisTemplate.opsForValue().set(key, String.valueOf(count));
            log.debug("unread 캐시 복구: accountId={}, count={}", accountId, count);
        }
        return UnreadCountResponseDto.of(count, notificationRepository.countUnreadByCategory(accountId));
    }

    // ===================== 읽음 처리 =====================

    @Transactional
    public void markAsRead(Long accountId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndAccount_AccountId(notificationId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (notification.isUnread()) {
            notification.markAsRead();
            // Redis 감소·SSE 전송은 커밋 후 — 커밋 전에 하면 롤백 시 카운트가 어긋나고,
            // SSE를 받은 프론트가 재조회했을 때 아직 커밋되지 않은 이전 상태를 읽을 수 있다.
            runAfterCommit(() -> {
                decrementUnreadCount(accountId);
                pushUnreadCount(accountId);
            });
        }
    }

    @Transactional
    public void markAllAsRead(Long accountId) {
        int updated = notificationRepository.markAllAsReadByAccountId(accountId, LocalDateTime.now());
        if (updated > 0) {
            runAfterCommit(() -> {
                clearUnreadCount(accountId);
                pushUnreadCount(accountId);
            });
        }
        log.debug("모두 읽음: accountId={}, count={}", accountId, updated);
    }

    // 참조 대상 기준 일괄 읽음 처리 — 예: 채팅방 읽음 시 해당 방의 CHAT_MESSAGE 알림 동기화
    // 대상: (accountId, type, refType, refId)와 일치하는 미읽음 알림 전부
    @Transactional
    public void markAsReadByRef(Long accountId, NotificationType type, NotificationRefType refType, Long refId) {
        int updated = notificationRepository.markAsReadByAccountAndTypeAndRef(
                accountId, type, refType, refId, LocalDateTime.now());
        if (updated > 0) {
            runAfterCommit(() -> {
                refreshUnreadCache(accountId);
                pushUnreadCount(accountId);
            });
        }
        log.debug("참조 기준 읽음 처리: accountId={}, type={}, refType={}, refId={}, count={}",
                accountId, type, refType, refId, updated);
    }

    // ===================== 삭제 =====================

    @Transactional
    public void deleteNotification(Long accountId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndAccount_AccountId(notificationId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        boolean wasUnread = notification.isUnread();
        notificationRepository.delete(notification);
        if (wasUnread) {
            runAfterCommit(() -> {
                decrementUnreadCount(accountId);
                pushUnreadCount(accountId);
            });
        }
    }

    @Transactional
    public void deleteAllByAccountId(Long accountId) {
        notificationRepository.deleteAllByAccount_AccountId(accountId);
        runAfterCommit(() -> {
            clearUnreadCount(accountId);
            pushUnreadCount(accountId);
        });
    }

    // 대상 도메인 삭제 시 연관 알림 일괄 삭제 (다른 서비스에서 호출)
    @Transactional
    public void deleteAllByRefTypeAndRefId(NotificationRefType refType, Long refId) {
        notificationRepository.deleteAllByRefTypeAndRefId(refType, refId);
    }

    // ===================== 내부 헬퍼 =====================

    // 현재 unread 카운트(전체 + 카테고리별)를 SSE로 전송 — 미연결 계정은 카테고리 집계 쿼리 없이 스킵
    private void pushUnreadCount(Long accountId) {
        if (!sseEmitterManager.isConnected(accountId)) return;
        sseEmitterManager.sendUnreadCount(accountId, getUnreadCount(accountId));
    }

    private void publishPushEvent(Account account, NotificationCreateRequestDto request) {
        PushMessage pushMessage = PushMessage.builder()
                .fcmToken(account.getFcmToken())
                .title(request.getTitle())
                .body(request.getContent())
                .linkUrl(request.getLinkUrl())
                .data(Map.of(
                        "type",    request.getType().name(),
                        "refType", request.getRefType() != null ? request.getRefType().name() : "",
                        "refId",   request.getRefId()   != null ? String.valueOf(request.getRefId()) : ""
                ))
                .build();
        eventPublisher.publishEvent(new NotificationPushEvent(pushMessage, account.getAccountId()));
    }

    // Redis INCR 후 현재 값 반환
    private long incrementUnreadCount(Long accountId) {
        Long val = redisTemplate.opsForValue().increment(UNREAD_KEY_PREFIX + accountId);
        return val != null ? val : 0L;
    }

    // 0보다 클 때만 DECR — GET→검사→DECR로 나누면 동시 읽음 처리 시 둘 다 검사를 통과해 -1이 될 수 있으므로
    // Lua로 원자적으로 처리한다.
    private static final DefaultRedisScript<Long> DECR_IF_POSITIVE = new DefaultRedisScript<>(
            "local v = tonumber(redis.call('get', KEYS[1]) or '0') "
                    + "if v > 0 then return redis.call('decr', KEYS[1]) else return 0 end",
            Long.class);

    // Redis DECR 후 현재 값 반환 (0 미만으로 내려가지 않음)
    private long decrementUnreadCount(Long accountId) {
        String key = UNREAD_KEY_PREFIX + accountId;
        Long result = redisTemplate.execute(DECR_IF_POSITIVE, List.of(key));
        return result != null ? result : 0L;
    }

    private void clearUnreadCount(Long accountId) {
        redisTemplate.delete(UNREAD_KEY_PREFIX + accountId);
    }

    // 일괄 읽음 처리처럼 감소량이 가변적인 경우 DB 기준으로 Redis 캐시를 재설정한다 (커밋 후 호출 전제)
    private void refreshUnreadCache(Long accountId) {
        long dbCount = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
        redisTemplate.opsForValue().set(UNREAD_KEY_PREFIX + accountId, String.valueOf(dbCount));
    }

    // 트랜잭션이 있으면 커밋 후 실행, 없으면 즉시 실행
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
