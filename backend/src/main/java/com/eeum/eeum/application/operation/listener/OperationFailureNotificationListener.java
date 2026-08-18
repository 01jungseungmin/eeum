package com.eeum.eeum.application.operation.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.common.lock.RateLimitKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.operation.event.OperationFailureRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;

/**
 * 운영 실패를 관리자에게 알린다.
 *
 * <p>이 알림은 "대시보드를 열어보라"는 신호다. 실패 내역 자체는
 * {@code GET /admin/operations/failures}에 이미 다 쌓여 있으므로, 여기서 건별 상세를 전부
 * 전달하려 하지 않는다.
 *
 * <p><b>분류 단위 쿨다운</b>이 이 리스너의 핵심이다. PortOne 장애처럼 하나의 원인으로
 * 실패가 초당 수십 건씩 쏟아지면, 스로틀 없이는 관리자 수 × 실패 건수만큼 알림이 생성된다.
 * 알림 테이블·FCM 발송·SSE가 동시에 폭증하고, 정작 다른 분류의 실패는 그 속에 묻힌다.
 * 그래서 분류별로 {@link #ALERT_COOLDOWN} 동안 1건만 내보낸다.
 *
 * <p>쿨다운에 막힌 실패도 이력에는 빠짐없이 남는다 — 알림만 생략될 뿐 유실되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureNotificationListener {

    /** 같은 분류의 실패 알림은 이 시간 동안 1건만 발송한다. */
    static final Duration ALERT_COOLDOWN = Duration.ofMinutes(10);

    private final NotificationService notificationService;
    private final AccountRepository accountRepository;
    private final RateLimitService rateLimitService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationFailureRecorded(OperationFailureRecordedEvent event) {
        // 분류는 DB에서 not null이라 실제로는 항상 존재한다. null 분기는 링크가 깨지지 않게 하는 방어다.
        String category = event.category() == null ? null : event.category().name();
        String label = category == null ? "UNKNOWN" : category;

        // 수신자를 먼저 확인한다. 관리자가 없는데 쿨다운만 소모하면
        // 이후 관리자가 생겨도 남은 쿨다운 동안 알림이 조용히 사라진다.
        List<Long> adminIds = accountRepository.findAdminAccountIds();
        if (adminIds.isEmpty()) {
            log.warn("운영 실패 알림 — 관리자 계정 없음: logId={}, category={}",
                    event.operationFailureLogId(), label);
            return;
        }

        if (!rateLimitService.tryAcquireCooldown(
                RateLimitKeys.operationFailureAlert(category), ALERT_COOLDOWN)) {
            log.debug("운영 실패 알림 스로틀 — category={}, logId={}",
                    label, event.operationFailureLogId());
            return;
        }

        String content = buildContent(label, event.operation(), event.errorCode());

        List<NotificationCreateRequestDto> requests = adminIds.stream()
                .map(adminId -> NotificationCreateRequestDto.builder()
                        .accountId(adminId)
                        .type(NotificationType.OPERATION_FAILURE_DETECTED)
                        .title("운영 실패가 감지되었습니다")
                        .content(content)
                        .refType(NotificationRefType.OPERATION_FAILURE)
                        .refId(event.operationFailureLogId())
                        .linkUrl(buildLinkUrl(category))
                        .build())
                .toList();

        notificationService.createNotificationsBatch(requests);
    }

    // 분류가 없으면 필터를 붙이지 않는다 — ?category=UNKNOWN은 서버가 거부하는 값이라
    // 관리자가 알림을 눌렀을 때 목록 대신 400을 보게 된다.
    private String buildLinkUrl(String category) {
        return category == null
                ? "/admin/operations/failures"
                : "/admin/operations/failures?category=" + category;
    }

    private String buildContent(String category, String operation, String errorCode) {
        String reason = (errorCode == null || errorCode.isBlank()) ? "원인 미상" : errorCode;
        return String.format("[%s] %s 실패 (%s). 같은 분류의 추가 실패는 대시보드에서 확인하세요.",
                category, operation, reason);
    }
}
