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

    @Transactional(readOnly = true)
    public NotificationSettingsResponseDto getMySettings(Long accountId) {
        NotificationSettings settings = getOrCreateSettings(accountId);
        return NotificationSettingsResponseDto.from(settings);
    }

    // ===================== 수정 =====================

    /**
     * 알림 설정 부분 업데이트.
     * null 필드는 변경하지 않는다.
     * ORDER / RESERVATION / SYSTEM은 필수 알림이므로 서버에서 무시한다.
     * marketingAgreedAt 법적 증빙 시각은 agreeToMarketing() / disagreeToMarketing() 에서 자동 관리.
     */
    @Transactional
    public NotificationSettingsResponseDto updateSettings(
            Long accountId,
            NotificationSettingsUpdateRequestDto request
    ) {
        NotificationSettings settings = getOrCreateSettings(accountId);

        //푸시 ON/OFF
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

    /** 발송 전 수신 동의 여부 검증 */
    @Transactional(readOnly = true)
    public boolean isAllowedForAccount(Long accountId, NotificationType type) {
        return settingsRepository.findByAccount_AccountId(accountId)
                .map(s -> s.isAllowed(type))
                .orElse(true);
    }

    // ===================== 내부 헬퍼 =====================

    /** 설정이 없으면 기본값으로 자동 생성한다 (지연 초기화) */
    private NotificationSettings getOrCreateSettings(Long accountId) {
        return settingsRepository.findByAccount_AccountId(accountId)
                .orElseGet(() -> {
                    Account account = accountRepository.findById(accountId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
                    NotificationSettings defaults = NotificationSettings.createDefault(account);
                    return settingsRepository.save(defaults);
                });
    }
}
