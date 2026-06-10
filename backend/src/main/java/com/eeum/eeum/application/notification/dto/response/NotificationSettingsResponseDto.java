package com.eeum.eeum.application.notification.dto.response;

import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
@Schema(description = "알림 수신 설정 응답")
public class NotificationSettingsResponseDto {

    // ─── 푸시 ON/OFF ───────────────────────────────────────────────────────

    @Schema(description = "주문/결제 알림 (필수, 변경 불가)", example = "true")
    private boolean orderEnabled;

    @Schema(description = "예약 알림 (필수, 변경 불가)", example = "true")
    private boolean reservationEnabled;

    @Schema(description = "채팅 알림", example = "true")
    private boolean chatEnabled;

    @Schema(description = "커뮤니티 알림", example = "true")
    private boolean communityEnabled;

    @Schema(description = "상점/리뷰 알림", example = "true")
    private boolean storeReviewEnabled;

    @Schema(description = "중고거래 알림", example = "true")
    private boolean usedProductEnabled;

    @Schema(description = "시스템 공지 (필수, 변경 불가)", example = "true")
    private boolean systemEnabled;

    @Schema(description = "재고 경고 알림 (사장님)", example = "true")
    private boolean stockEnabled;

    @Schema(description = "정산 완료 알림 (사장님)", example = "true")
    private boolean settlementEnabled;

    @Schema(description = "마케팅/이벤트 알림", example = "false")
    private boolean marketingEnabled;

    @Schema(description = "마케팅 수신 동의 시각")
    private LocalDateTime marketingAgreedAt;

    //방해 금지 (DND)

    @Schema(description = "방해 금지 모드 활성화 여부", example = "false")
    private boolean dndEnabled;

    @Schema(description = "방해 금지 시작 시각 (HH:mm)", example = "22:00")
    private LocalTime dndStartTime;

    @Schema(description = "방해 금지 종료 시각 (HH:mm)", example = "08:00")
    private LocalTime dndEndTime;

    //이메일 수신 설정

    @Schema(description = "주문 이메일 수신", example = "true")
    private boolean orderEmailEnabled;

    @Schema(description = "채팅 이메일 수신", example = "false")
    private boolean chatEmailEnabled;

    @Schema(description = "리뷰 이메일 수신", example = "true")
    private boolean reviewEmailEnabled;

    @Schema(description = "예약 이메일 수신", example = "false")
    private boolean reservationEmailEnabled;

    @Schema(description = "재고 이메일 수신", example = "true")
    private boolean stockEmailEnabled;

    @Schema(description = "정산 이메일 수신", example = "true")
    private boolean settlementEmailEnabled;

    //알림음 설정

    @Schema(description = "주문 알림음", example = "true")
    private boolean orderSoundEnabled;

    @Schema(description = "채팅 알림음", example = "true")
    private boolean chatSoundEnabled;

    @Schema(description = "리뷰 알림음", example = "false")
    private boolean reviewSoundEnabled;

    @Schema(description = "예약 알림음", example = "true")
    private boolean reservationSoundEnabled;

    @Schema(description = "재고 알림음", example = "false")
    private boolean stockSoundEnabled;

    @Schema(description = "정산 알림음", example = "false")
    private boolean settlementSoundEnabled;

    //팩토리 메서드

    public static NotificationSettingsResponseDto from(NotificationSettings notificationSettings) {
        return NotificationSettingsResponseDto.builder()
                // 푸시
                .orderEnabled(notificationSettings.isOrderEnabled())
                .reservationEnabled(notificationSettings.isReservationEnabled())
                .chatEnabled(notificationSettings.isChatEnabled())
                .communityEnabled(notificationSettings.isCommunityEnabled())
                .storeReviewEnabled(notificationSettings.isStoreReviewEnabled())
                .usedProductEnabled(notificationSettings.isUsedProductEnabled())
                .systemEnabled(notificationSettings.isSystemEnabled())
                .stockEnabled(notificationSettings.isStockEnabled())
                .settlementEnabled(notificationSettings.isSettlementEnabled())
                .marketingEnabled(notificationSettings.isMarketingEnabled())
                .marketingAgreedAt(notificationSettings.getMarketingAgreedAt())
                // DND
                .dndEnabled(notificationSettings.isDndEnabled())
                .dndStartTime(notificationSettings.getDndStartTime())
                .dndEndTime(notificationSettings.getDndEndTime())
                // 이메일
                .orderEmailEnabled(notificationSettings.isOrderEmailEnabled())
                .chatEmailEnabled(notificationSettings.isChatEmailEnabled())
                .reviewEmailEnabled(notificationSettings.isReviewEmailEnabled())
                .reservationEmailEnabled(notificationSettings.isReservationEmailEnabled())
                .stockEmailEnabled(notificationSettings.isStockEmailEnabled())
                .settlementEmailEnabled(notificationSettings.isSettlementEmailEnabled())
                // 알림음
                .orderSoundEnabled(notificationSettings.isOrderSoundEnabled())
                .chatSoundEnabled(notificationSettings.isChatSoundEnabled())
                .reviewSoundEnabled(notificationSettings.isReviewSoundEnabled())
                .reservationSoundEnabled(notificationSettings.isReservationSoundEnabled())
                .stockSoundEnabled(notificationSettings.isStockSoundEnabled())
                .settlementSoundEnabled(notificationSettings.isSettlementSoundEnabled())
                .build();
    }
}
