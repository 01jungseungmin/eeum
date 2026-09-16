package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.EventNoticeRequestDto;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.dto.request.SystemNoticeRequestDto;
import com.eeum.eeum.application.notification.dto.request.NotificationAdminSearchDto;
import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private static final int BATCH_SIZE = 100; // 청크 단위

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final AccountRepository accountRepository;

    // 전체 또는 특정 대상에게 시스템 공지 발송 @Async — 대량 발송이므로 비동기 처리
    @Async
    @Transactional
    public void sendSystemNotice(SystemNoticeRequestDto request) {
        List<Long> targetIds = resolveTargetIds(request.getTargetAccountIds());
        log.info("시스템 공지 발송 시작: targetCount={}", targetIds.size());

        // 청크 단위로 분할 처리
        for (int i = 0; i < targetIds.size(); i += BATCH_SIZE) {
            List<Long> chunk = targetIds.subList(i, Math.min(i + BATCH_SIZE, targetIds.size()));
            List<NotificationCreateRequestDto> requests = chunk.stream()
                    .map(accountId -> NotificationCreateRequestDto.builder()
                            .accountId(accountId)
                            .type(NotificationType.SYSTEM_NOTICE)
                            .title(request.getTitle())
                            .content(request.getContent())
                            .refType(NotificationRefType.SYSTEM)
                            .refId(null)
                            .linkUrl(request.getLinkUrl())
                            .build())
                    .collect(Collectors.toList());
            notificationService.createNotificationsBatch(requests);
        }
        log.info("시스템 공지 발송 완료: targetCount={}", targetIds.size());
    }

    // 마케팅 수신 동의자에게 이벤트 알림 발송
    @Async
    @Transactional
    public void sendEventNotice(EventNoticeRequestDto request) {
        // 마케팅 수신 동의 계정만 조회
        List<Long> targetIds = settingsRepository.findAccountIdsByMarketingEnabled();


        log.info("이벤트 알림 발송 시작: targetCount={}", targetIds.size());

        for (int i = 0; i < targetIds.size(); i += BATCH_SIZE) {
            List<Long> chunk = targetIds.subList(i, Math.min(i + BATCH_SIZE, targetIds.size()));
            List<NotificationCreateRequestDto> requests = chunk.stream()
                    .map(accountId -> NotificationCreateRequestDto.builder()
                            .accountId(accountId)
                            .type(NotificationType.MARKETING_EVENT)
                            .title(request.getTitle())
                            .content(request.getContent())
                            .refType(NotificationRefType.SYSTEM)
                            .refId(null)
                            .linkUrl(request.getLinkUrl())
                            .build())
                    .collect(Collectors.toList());
            notificationService.createNotificationsBatch(requests);
        }
        log.info("이벤트 알림 발송 완료: targetCount={}", targetIds.size());
    }

    // 발송 이력 조회 (관리자 감사용)
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getNotificationHistory(
            NotificationAdminSearchDto condition,
            Pageable pageable
    ) {
        return notificationRepository.searchAdminNotifications(condition, pageable)
                .map(NotificationResponseDto::from);
    }

    // ===================== 내부 헬퍼 =====================

    private List<Long> resolveTargetIds(List<Long> targetAccountIds) {
        if (targetAccountIds != null && !targetAccountIds.isEmpty()) {
            return targetAccountIds;
        }
        // null이면 전체 ACTIVE 계정 대상
        return accountRepository.findAllActiveAccountIds();
    }
}
