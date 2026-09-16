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

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UnreadCountService unreadCountService;
    private final UnreadSyncExecutor unreadSyncExecutor;
    private final SseEmitterManager sseEmitterManager;

    // ===================== SSE 구독 =====================

    // 클라이언트(웹)가 SSE 구독을 요청할 때 호출
    // 연결 직후 현재 unread 카운트를 즉시 전송한다. 캐시가 살아 있으면 조회는 Redis에서 끝나고
    // DB 커넥션을 잡지 않는다 — 미스일 때만 UnreadSnapshotRebuilder가 트랜잭션을 연다
    public SseEmitter subscribe(Long accountId, Long tokenVersion, String tokenFingerprint) {
        SseEmitter emitter = sseEmitterManager.subscribe(accountId, tokenVersion, tokenFingerprint);
        try {
            // 구독 직후 현재 카운트를 즉시 전달 (페이지 진입 시 배지 즉시 표시)
            sseEmitterManager.sendUnreadCount(accountId, unreadCountService.getUnreadCount(accountId));
            return emitter;
        } catch (RuntimeException e) {
            sseEmitterManager.closeIfCurrent(accountId, emitter);
            throw e;
        }
    }

    // ===================== 알림 생성 (다른 도메인 서비스에서 호출) =====================

    /**
     * 단건 알림 생성
     * 1. 수신 동의 여부 확인
     * 2. Notification 저장 후 DB 기준 Redis unread 스냅샷 갱신
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
        synchronizeUnreadAfterCommit(targetAccountId);

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

    // 안 읽은 알림 수 — 조회/캐싱 로직은 UnreadCountService가 전담
    public UnreadCountResponseDto getUnreadCount(Long accountId) {
        return unreadCountService.getUnreadCount(accountId);
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
            synchronizeUnreadAfterCommit(accountId);
        }
    }

    @Transactional
    public void markAllAsRead(Long accountId) {
        int updated = notificationRepository.markAllAsReadByAccountId(accountId, LocalDateTime.now());
        if (updated > 0) {
            synchronizeUnreadAfterCommit(accountId);
        }
        log.debug("모두 읽음: accountId={}, count={}", accountId, updated);
    }

    // 카테고리 탭 진입 시 해당 카테고리의 미읽음 알림을 일괄 읽음 처리하고
    // 커밋 이후 Redis 전체/카테고리 카운트와 SSE 배지를 DB 기준으로 동기화한다.
    @Transactional
    public void markCategoryAsRead(Long accountId, NotificationCategory category) {
        int updated = notificationRepository.markAsReadByAccountIdAndTypes(
                accountId, category.getTypes(), LocalDateTime.now());
        if (updated > 0) {
            synchronizeUnreadAfterCommit(accountId);
        }
        log.debug("카테고리 읽음 처리: accountId={}, category={}, count={}",
                accountId, category, updated);
    }

    // 참조 대상 기준 일괄 읽음 처리 — 예: 채팅방 읽음 시 해당 방의 CHAT_MESSAGE 알림 동기화
    // 대상: (accountId, type, refType, refId)와 일치하는 미읽음 알림 전부
    @Transactional
    public void markAsReadByRef(Long accountId, NotificationType type, NotificationRefType refType, Long refId) {
        int updated = notificationRepository.markAsReadByAccountAndTypeAndRef(
                accountId, type, refType, refId, LocalDateTime.now());
        if (updated > 0) {
            synchronizeUnreadAfterCommit(accountId);
        }
        log.debug("참조 기준 읽음 처리: accountId={}, type={}, refType={}, refId={}, count={}",
                accountId, type, refType, refId, updated);
    }

    // 여러 사용자의 동일 참조 알림 일괄 읽음 처리 — 채팅방 종료처럼 참여자 전원을 한 번에 정리할 때 사용.
    // 참여자마다 markAsReadByRef를 호출하면 UPDATE와 unread 재계산이 인원수만큼 반복된다.
    @Transactional
    public void markAsReadByRefForAccounts(
            List<Long> accountIds, NotificationType type, NotificationRefType refType, Long refId) {
        if (accountIds == null || accountIds.isEmpty()) {
            return;
        }
        int updated = notificationRepository.markAsReadByAccountsAndTypeAndRef(
                accountIds, type, refType, refId, LocalDateTime.now());
        if (updated > 0) {
            runAfterCommit(() -> synchronizeUnread(accountIds));
        }
        log.debug("참조 기준 일괄 읽음 처리: accounts={}, type={}, refType={}, refId={}, count={}",
                accountIds.size(), type, refType, refId, updated);
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
            synchronizeUnreadAfterCommit(accountId);
        }
    }

    @Transactional
    public void deleteAllByAccountId(Long accountId) {
        notificationRepository.deleteAllByAccount_AccountId(accountId);
        synchronizeUnreadAfterCommit(accountId);
    }

    // 대상 도메인 삭제 시 연관 알림 일괄 삭제 (다른 서비스에서 호출)
    @Transactional
    public void deleteAllByRefTypeAndRefId(NotificationRefType refType, Long refId) {
        notificationRepository.deleteAllByRefTypeAndRefId(refType, refId);
    }

    // ===================== 내부 헬퍼 =====================

    // 커밋 이후의 unread 동기화.
    //
    // 요청 스레드에서 하는 일은 Redis 키 삭제뿐이고, DB 재계산과 실시간 전송은 async로 넘긴다.
    // afterCommit은 커밋을 수행한 그 요청 스레드에서 돌고 이 시점 바깥 커넥션은 아직 반납 전이라,
    // 여기서 트랜잭션을 열면 한 요청이 커넥션을 2개 점유한다(자원 예산 문서 R2).
    //
    // 무효화를 먼저 하는 순서가 중요하다. async 작업은 큐 포화 시 폐기될 수 있는데,
    // 캐시를 비워 두면 폐기되더라도 다음 조회가 미스로 DB에서 정확히 복구한다.
    // 순서를 뒤집으면 "완성됐지만 낡은" 스냅샷이 남아 조회가 계속 그것을 믿는다.
    private void synchronizeUnreadAfterCommit(Long accountId) {
        runAfterCommit(() -> synchronizeUnread(accountId));
    }

    // DB 변경은 이미 커밋된 뒤다. 여기서 예외가 새어 나가면 afterCommit을 실행한 요청이
    // 커밋에 성공하고도 실패로 응답되므로, Redis 장애든 큐 거부든 전부 삼킨다.
    private void synchronizeUnread(Long accountId) {
        try {
            long generation = unreadCountService.invalidateSnapshot(accountId);
            unreadSyncExecutor.rebuildAndPush(accountId, generation);
        } catch (RuntimeException e) {
            log.error("커밋 후 unread 동기화 제출 실패: accountId={}", accountId, e);
        }
    }

    private void synchronizeUnread(List<Long> accountIds) {
        try {
            Map<Long, Long> generations = unreadCountService.invalidateSnapshots(accountIds);
            unreadSyncExecutor.rebuildAndPushAll(generations);
        } catch (RuntimeException e) {
            log.error("커밋 후 unread 동기화 제출 실패: accounts={}", accountIds.size(), e);
        }
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
