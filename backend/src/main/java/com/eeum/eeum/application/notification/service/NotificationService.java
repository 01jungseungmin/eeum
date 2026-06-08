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

    /** Redis 키 패턴: unread:account:{accountId} */
    private static final String UNREAD_KEY_PREFIX = "unread:account:";

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final SseEmitterManager sseEmitterManager;

    // ===================== SSE 구독 =====================

    /**
     * 클라이언트(웹)가 SSE 구독을 요청할 때 호출.
     * 연결 직후 현재 unread 카운트를 즉시 전송한다.
     */
    public SseEmitter subscribe(Long accountId) {
        SseEmitter emitter = sseEmitterManager.subscribe(accountId);
        // 구독 직후 현재 카운트를 즉시 전달 (페이지 진입 시 배지 즉시 표시)
        long count = getUnreadCount(accountId).getUnreadCount();
        sseEmitterManager.sendUnreadCount(accountId, count);
        return emitter;
    }

    // ===================== 알림 생성 (다른 도메인 서비스에서 호출) =====================

    /**
     * 단건 알림 생성.
     * 1. 수신 동의 여부 확인
     * 2. Notification 저장 + Redis unread INCR
     * 3. SSE로 웹 클라이언트에 unread 카운트 즉시 전달
     * 4. DND가 비활성 상태이고 FCM 토큰이 있으면 NotificationPushEvent 발행 (AFTER_COMMIT + @Async → FCM)
     */
    @Transactional
    public NotificationResponseDto createNotification(NotificationCreateRequestDto request) {
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

        // 3. Redis unread 카운트 증가
        long newCount = incrementUnreadCount(account.getAccountId());

        // 4. SSE — 웹 클라이언트 배지 실시간 갱신
        sseEmitterManager.sendUnreadCount(account.getAccountId(), newCount);

        // 5. FCM 푸시 이벤트 발행 (DND 비활성 + 토큰 있는 경우만)
        boolean dndActive = settingsOpt.map(NotificationSettings::isDndActive).orElse(false);
        if (!dndActive && account.getFcmToken() != null) {
            publishPushEvent(account, request);
        } else if (dndActive) {
            log.debug("DND 활성 — 푸시 스킵: accountId={}", account.getAccountId());
        }

        log.debug("알림 생성: accountId={}, type={}", account.getAccountId(), request.getType());
        return NotificationResponseDto.from(notification);
    }

    /**
     * 다수 사용자 일괄 알림 생성 (그룹 채팅, 시스템 공지 등).
     * 거부된 사용자는 건너뛰고 나머지만 저장한다.
     */
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

    /**
     * 카테고리별 알림 조회 (웹 UI 탭 필터).
     * category == null 이면 전체 조회.
     */
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

    /**
     * 안 읽은 알림 수 — Redis 우선 조회, 캐시 미스 시 DB fallback 후 Redis 복구.
     */
    @Transactional(readOnly = true)
    public UnreadCountResponseDto getUnreadCount(Long accountId) {
        String key = UNREAD_KEY_PREFIX + accountId;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return UnreadCountResponseDto.of(Long.parseLong(cached));
        }

        long count = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
        redisTemplate.opsForValue().set(key, String.valueOf(count));
        log.debug("unread 캐시 복구: accountId={}, count={}", accountId, count);
        return UnreadCountResponseDto.of(count);
    }

    // ===================== 읽음 처리 =====================

    @Transactional
    public void markAsRead(Long accountId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndAccount_AccountId(notificationId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (notification.isUnread()) {
            notification.markAsRead();
            long newCount = decrementUnreadCount(accountId);
            sseEmitterManager.sendUnreadCount(accountId, newCount);
        }
    }

    @Transactional
    public void markAllAsRead(Long accountId) {
        int updated = notificationRepository.markAllAsReadByAccountId(accountId, LocalDateTime.now());
        if (updated > 0) {
            clearUnreadCount(accountId);
            sseEmitterManager.sendUnreadCount(accountId, 0L);
        }
        log.debug("모두 읽음: accountId={}, count={}", accountId, updated);
    }

    // ===================== 삭제 =====================

    @Transactional
    public void deleteNotification(Long accountId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndAccount_AccountId(notificationId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (notification.isUnread()) {
            long newCount = decrementUnreadCount(accountId);
            sseEmitterManager.sendUnreadCount(accountId, newCount);
        }
        notificationRepository.delete(notification);
    }

    @Transactional
    public void deleteAllByAccountId(Long accountId) {
        notificationRepository.deleteAllByAccount_AccountId(accountId);
        clearUnreadCount(accountId);
        sseEmitterManager.sendUnreadCount(accountId, 0L);
    }

    /** 대상 도메인 삭제 시 연관 알림 일괄 삭제 (다른 서비스에서 호출) */
    @Transactional
    public void deleteAllByRefTypeAndRefId(NotificationRefType refType, Long refId) {
        notificationRepository.deleteAllByRefTypeAndRefId(refType, refId);
    }

    // ===================== 내부 헬퍼 =====================

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

    /** Redis INCR 후 현재 값 반환 */
    private long incrementUnreadCount(Long accountId) {
        Long val = redisTemplate.opsForValue().increment(UNREAD_KEY_PREFIX + accountId);
        return val != null ? val : 0L;
    }

    /** Redis DECR 후 현재 값 반환 (0 미만으로 내려가지 않음) */
    private long decrementUnreadCount(Long accountId) {
        String key = UNREAD_KEY_PREFIX + accountId;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null && Long.parseLong(val) > 0) {
            Long result = redisTemplate.opsForValue().decrement(key);
            return result != null ? result : 0L;
        }
        return 0L;
    }

    private void clearUnreadCount(Long accountId) {
        redisTemplate.delete(UNREAD_KEY_PREFIX + accountId);
    }
}
