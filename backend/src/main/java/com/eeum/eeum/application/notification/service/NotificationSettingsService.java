package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.request.NotificationSettingsUpdateRequestDto;
import com.eeum.eeum.application.notification.dto.response.NotificationSettingsResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final NotificationSettingsRepository settingsRepository;
    private final AccountRepository accountRepository;

    // ===================== 조회 =====================

    @Transactional
    public NotificationSettingsResponseDto getMySettings(Long accountId) {
        NotificationSettings settings = getOrCreateSettings(accountId);
        return NotificationSettingsResponseDto.from(settings);
    }

    // ===================== 수정 =====================

    // 알림 설정 부분 업데이트(null 필드는 변경 X)
    @Transactional
    public NotificationSettingsResponseDto updateSettings(
            Long accountId,
            NotificationSettingsUpdateRequestDto request
    ) {
        // 동일 계정의 부분 PATCH를 직렬화해 서로 다른 필드 변경이 마지막 커밋에 덮이지 않게 한다.
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        NotificationSettings settings = settingsRepository.findByAccount_AccountId(accountId)
                .orElseGet(() -> settingsRepository.save(NotificationSettings.createDefault(account)));

        //푸시 ON/OFF
        if (request.getAllEnabled() != null
                && request.getAllEnabled() != settings.isAllEnabled()) {
            settings.updateAllEnabled(request.getAllEnabled());
        }
        if (request.getChatEnabled() != null
                && request.getChatEnabled() != settings.isChatEnabled()) {
            settings.toggleChatEnabled();
        }
        if (request.getCommunityEnabled() != null
                && request.getCommunityEnabled() != settings.isCommunityEnabled()) {
            settings.toggleCommunityEnabled();
        }
        if (request.getStoreReviewEnabled() != null
                && request.getStoreReviewEnabled() != settings.isStoreReviewEnabled()) {
            settings.toggleStoreReviewEnabled();
        }
        if (request.getUsedProductEnabled() != null
                && request.getUsedProductEnabled() != settings.isUsedProductEnabled()) {
            settings.toggleUsedProductEnabled();
        }
        if (request.getStockEnabled() != null
                && request.getStockEnabled() != settings.isStockEnabled()) {
            settings.toggleStockEnabled();
        }
        if (request.getSettlementEnabled() != null
                && request.getSettlementEnabled() != settings.isSettlementEnabled()) {
            settings.toggleSettlementEnabled();
        }

        //마케팅 수신 동의/거부
        if (request.getMarketingEnabled() != null) {
            if (request.getMarketingEnabled() && !settings.isMarketingEnabled()) {
                settings.agreeToMarketing();
            } else if (!request.getMarketingEnabled() && settings.isMarketingEnabled()) {
                settings.disagreeToMarketing();
            }
        }

        //DND 설정
        if (request.getDndEnabled() != null) {
            settings.updateDnd(
                    request.getDndEnabled(),
                    request.getDndStartTime(),
                    request.getDndEndTime()
            );
        }

        //이메일 수신 설정
        settings.updateEmailSettings(
                request.getOrderEmailEnabled(),
                request.getChatEmailEnabled(),
                request.getReviewEmailEnabled(),
                request.getReservationEmailEnabled(),
                request.getStockEmailEnabled(),
                request.getSettlementEmailEnabled()
        );

        //알림음 설정
        settings.updateSoundSettings(
                request.getOrderSoundEnabled(),
                request.getChatSoundEnabled(),
                request.getReviewSoundEnabled(),
                request.getReservationSoundEnabled(),
                request.getStockSoundEnabled(),
                request.getSettlementSoundEnabled()
        );

        log.info("알림 설정 변경: accountId={}", accountId);
        return NotificationSettingsResponseDto.from(settings);
    }

    // 발송 전 수신 동의 여부 검증
    @Transactional(readOnly = true)
    public boolean isAllowedForAccount(Long accountId, NotificationType type) {
        return settingsRepository.findByAccount_AccountId(accountId)
                .map(s -> s.isAllowed(type))
                .orElse(true);
    }

    // ===================== 내부 헬퍼 =====================

    // 설정이 없으면 기본값으로 자동 생성한다 (지연 초기화)
    private NotificationSettings getOrCreateSettings(Long accountId) {
        // MySQL REPEATABLE_READ 스냅샷이 만들어지기 전에 Account 행을 먼저 잠근다.
        // 그래야 락 대기 중 다른 트랜잭션이 생성한 설정도 이후 최초 조회에서 확인할 수 있다.
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        return settingsRepository.findByAccount_AccountId(accountId)
                .orElseGet(() -> settingsRepository.save(NotificationSettings.createDefault(account)));
    }
}
