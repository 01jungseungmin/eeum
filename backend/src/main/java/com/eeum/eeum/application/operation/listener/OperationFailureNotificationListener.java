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
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;

/** 운영 실패를 관리자에게 알린다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureNotificationListener {

    /** 같은 분류의 실패 알림은 이 시간 동안 1건만 발송한다. */
    static final Duration ALERT_COOLDOWN = Duration.ofMinutes(10);

    private final NotificationService notificationService;
    private final AccountRepository accountRepository;
    private final RateLimitService rateLimitService;

    /** 원장 저장 트랜잭션이 커밋된 뒤에만 알림을 만들고, 커밋 뒤 발행된 기존 이벤트도 받는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOperationFailureRecorded(OperationFailureRecordedEvent event) {
        // 분류는 DB에서 not null이라 실제로는 항상 존재한다. null 분기는 링크가 깨지지 않게 하는 방어다.
        String category = event.category() == null ? null : event.category().name();
        String label = category == null ? "UNKNOWN" : category;

        // 쿨다운을 먼저 잡는다. 수신자 조회를 앞에 두면 실패가 폭주할 때
        // 스로틀에 막힐 건까지 건별로 관리자 조회 쿼리를 날리게 된다.
        String cooldownKey = RateLimitKeys.operationFailureAlert(category);
        if (!rateLimitService.tryAcquireCooldown(cooldownKey, ALERT_COOLDOWN)) {
            log.debug("운영 실패 알림 스로틀 — category={}, logId={}",
                    label, event.operationFailureLogId());
            return;
        }

        List<Long> adminIds = accountRepository.findAdminAccountIds();
        if (adminIds.isEmpty()) {
            // 보낼 곳이 없으면 쿨다운을 반납한다. 그대로 두면 이후 관리자가 생겨도
            // 남은 쿨다운 동안 알림이 조용히 사라진다.
            rateLimitService.releaseCooldown(cooldownKey);
            log.warn("운영 실패 알림 — 관리자 계정 없음: logId={}, category={}",
                    event.operationFailureLogId(), label);
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
