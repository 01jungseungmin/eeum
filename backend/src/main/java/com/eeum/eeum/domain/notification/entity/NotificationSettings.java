package com.eeum.eeum.domain.notification.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_settings")
public class NotificationSettings extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_settings_id")
    private Long notificationsettingsId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    // 푸시 ON/OFF 토글

    // 주문/결제 알림 (필수 — UI에서 비활성화 불가)
    @Column(name = "order_enabled", nullable = false)
    private boolean orderEnabled = true;

    // 예약 알림 (필수)
    @Column(name = "reservation_enabled", nullable = false)
    private boolean reservationEnabled = true;

    // 채팅 알림
    @Column(name = "chat_enabled", nullable = false)
    private boolean chatEnabled = true;

    // 커뮤니티 알림 (댓글/대댓글/좋아요)
    @Column(name = "community_enabled", nullable = false)
    private boolean communityEnabled = true;

    // 상점/리뷰 알림
    @Column(name = "store_review_enabled", nullable = false)
    private boolean storeReviewEnabled = true;

    // 중고거래 알림
    @Column(name = "used_product_enabled", nullable = false)
    private boolean usedProductEnabled = true;

    // 시스템 공지 (필수)
    @Column(name = "system_enabled", nullable = false)
    private boolean systemEnabled = true;

    // 재고 알림 (사장님용)
    @Column(name = "stock_enabled", nullable = false)
    private boolean stockEnabled = true;

    // 정산 알림 (사장님용)
    @Column(name = "settlement_enabled", nullable = false)
    private boolean settlementEnabled = true;

    // 마케팅/이벤트 알림 (명시적 수신 동의 필요)
    @Column(name = "marketing_enabled", nullable = false)
    private boolean marketingEnabled = false;

    // 마케팅 수신 동의 시각 (법적 증빙)
    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    //방해 금지 (DND)

    @Column(name = "dnd_enabled", nullable = false)
    private boolean dndEnabled = false;

    // 방해 금지 시작 시각 (기본 22:00)
    @Column(name = "dnd_start_time", nullable = false)
    private LocalTime dndStartTime = LocalTime.of(22, 0);

    // 방해 금지 종료 시각 (기본 08:00, 자정 넘김 가능)
    @Column(name = "dnd_end_time", nullable = false)
    private LocalTime dndEndTime = LocalTime.of(8, 0);

    //이메일 수신 설정 (카테고리별)
    @Column(name = "order_email_enabled", nullable = false)
    private boolean orderEmailEnabled = true;

    @Column(name = "chat_email_enabled", nullable = false)
    private boolean chatEmailEnabled = false;

    @Column(name = "review_email_enabled", nullable = false)
    private boolean reviewEmailEnabled = true;

    @Column(name = "reservation_email_enabled", nullable = false)
    private boolean reservationEmailEnabled = false;

    @Column(name = "stock_email_enabled", nullable = false)
    private boolean stockEmailEnabled = true;

    @Column(name = "settlement_email_enabled", nullable = false)
    private boolean settlementEmailEnabled = true;

    //알림음 설정 (카테고리별)
    @Column(name = "order_sound_enabled", nullable = false)
    private boolean orderSoundEnabled = true;

    @Column(name = "chat_sound_enabled", nullable = false)
    private boolean chatSoundEnabled = true;

    @Column(name = "review_sound_enabled", nullable = false)
    private boolean reviewSoundEnabled = false;

    @Column(name = "reservation_sound_enabled", nullable = false)
    private boolean reservationSoundEnabled = true;

    @Column(name = "stock_sound_enabled", nullable = false)
    private boolean stockSoundEnabled = false;

    @Column(name = "settlement_sound_enabled", nullable = false)
    private boolean settlementSoundEnabled = false;

    // ===================== 정적 팩토리 =====================

    // 회원가입 시 기본 설정으로 생성
    public static NotificationSettings createDefault(Account account) {
        NotificationSettings notificationSettings = new NotificationSettings();
        notificationSettings.account            = account;
        notificationSettings.orderEnabled       = true;
        notificationSettings.reservationEnabled = true;
        notificationSettings.chatEnabled        = true;
        notificationSettings.communityEnabled   = true;
        notificationSettings.storeReviewEnabled = true;
        notificationSettings.usedProductEnabled = true;
        notificationSettings.systemEnabled      = true;
        notificationSettings.stockEnabled       = true;
        notificationSettings.settlementEnabled  = true;
        notificationSettings.marketingEnabled   = false;
        // DND 기본값
        notificationSettings.dndEnabled    = false;
        notificationSettings.dndStartTime  = LocalTime.of(22, 0);
        notificationSettings.dndEndTime    = LocalTime.of(8, 0);
        // 이메일 기본값
        notificationSettings.orderEmailEnabled       = true;
        notificationSettings.chatEmailEnabled        = false;
        notificationSettings.reviewEmailEnabled      = true;
        notificationSettings.reservationEmailEnabled = false;
        notificationSettings.stockEmailEnabled       = true;
        notificationSettings.settlementEmailEnabled  = true;
        // 알림음 기본값
        notificationSettings.orderSoundEnabled       = true;
        notificationSettings.chatSoundEnabled        = true;
        notificationSettings.reviewSoundEnabled      = false;
        notificationSettings.reservationSoundEnabled = true;
        notificationSettings.stockSoundEnabled       = false;
        notificationSettings.settlementSoundEnabled  = false;
        return notificationSettings;
    }

    // ===================== 도메인 메서드 =====================

    //  해당 타입의 푸시 수신 동의 여부
    //  ORDER / RESERVATION / SYSTEM / 관리자 전용 알림은 항상 true (필수)
    public boolean isAllowed(NotificationType type) {
        return switch (type) {
            // 필수 알림 (끌 수 없음)
            case ORDER_STATUS_CHANGED, PAYMENT_COMPLETED  -> true;
            case RESERVATION_CONFIRMED, RESERVATION_CANCELLED, RESERVATION_REJECTED, RESERVATION_REMINDER -> true;
            case SYSTEM_NOTICE                            -> true;
            case INQUIRY_ANSWERED                         -> true;
            // 사장님 필수
            case NEW_ORDER                                -> true;
            case NEW_RESERVATION                          -> true;
            // 관리자 전용 (항상 수신)
            case OWNER_APPLICATION_SUBMITTED, REPORT_SUBMITTED, INQUIRY_SUBMITTED -> true;

            // 선택 알림
            case CHAT_MESSAGE                             -> chatEnabled;
            case COMMUNITY_COMMENT, COMMUNITY_REPLY, COMMUNITY_LIKE -> communityEnabled;
            case COMMUNITY_ADMIN_ACTION                              -> true;
            case STORE_REVIEW, STORE_REVIEW_REPLY, STORE_PRODUCT_RESTOCK -> storeReviewEnabled;
            case USED_PRODUCT_INQUIRY, USED_REVIEW        -> usedProductEnabled;
            case STOCK_WARNING                            -> stockEnabled;
            case SETTLEMENT_COMPLETED                     -> settlementEnabled;
            case MARKETING_EVENT                          -> marketingEnabled;
        };
    }

    // 현재 시각이 방해 금지 구간 내에 있는지 판단
    // dndStartTime > dndEndTime 이면 자정 걸치는 범위 (예: 22:00 ~ 08:00)

    public boolean isDndActive() {
        if (!dndEnabled) return false;
        LocalTime now = LocalTime.now();
        if (dndStartTime.isBefore(dndEndTime)) {
            // 같은 날 구간 (예: 13:00 ~ 17:00)
            return !now.isBefore(dndStartTime) && now.isBefore(dndEndTime);
        } else {
            // 자정 걸치는 구간 (예: 22:00 ~ 08:00)
            return !now.isBefore(dndStartTime) || now.isBefore(dndEndTime);
        }
    }

    // 해당 알림 타입에 대한 이메일 수신 여부
    // 관리자/필수 알림 카테고리에 해당하지 않으면 false

    public boolean isEmailEnabled(NotificationType type) {
        NotificationCategory category = type.getCategory();
        return switch (category) {
            case ORDER       -> orderEmailEnabled;
            case CHAT        -> chatEmailEnabled;
            case REVIEW      -> reviewEmailEnabled;
            case RESERVATION -> reservationEmailEnabled;
            case PRODUCT     -> stockEmailEnabled;
            case COMMUNITY   -> false;
            case SYSTEM      -> settlementEmailEnabled;
        };
    }

    // 해당 알림 타입에 대한 알림음 활성화 여부.
    public boolean isSoundEnabled(NotificationType type) {
        NotificationCategory category = type.getCategory();
        return switch (category) {
            case ORDER       -> orderSoundEnabled;
            case CHAT        -> chatSoundEnabled;
            case REVIEW      -> reviewSoundEnabled;
            case RESERVATION -> reservationSoundEnabled;
            case PRODUCT     -> stockSoundEnabled;
            case COMMUNITY   -> false;
            case SYSTEM      -> settlementSoundEnabled;
        };
    }

    // ===================== 토글 메서드 =====================

    public void toggleChatEnabled()        { this.chatEnabled = !this.chatEnabled; }
    public void toggleCommunityEnabled()   { this.communityEnabled = !this.communityEnabled; }
    public void toggleStoreReviewEnabled() { this.storeReviewEnabled = !this.storeReviewEnabled; }
    public void toggleUsedProductEnabled() { this.usedProductEnabled = !this.usedProductEnabled; }
    public void toggleStockEnabled()       { this.stockEnabled = !this.stockEnabled; }
    public void toggleSettlementEnabled()  { this.settlementEnabled = !this.settlementEnabled; }

    // ===================== 업데이트 메서드 =====================

    // 마케팅 수신 동의
    public void agreeToMarketing() {
        this.marketingEnabled  = true;
        this.marketingAgreedAt = LocalDateTime.now();
    }

    // 마케팅 수신 거부
    public void disagreeToMarketing() {
        this.marketingEnabled  = false;
        this.marketingAgreedAt = null;
    }

    // 방해 금지 설정 변경
    public void updateDnd(boolean dndEnabled, LocalTime startTime, LocalTime endTime) {
        this.dndEnabled   = dndEnabled;
        this.dndStartTime = startTime != null ? startTime : this.dndStartTime;
        this.dndEndTime   = endTime   != null ? endTime   : this.dndEndTime;
    }

    // 이메일 수신 설정 일괄 업데이트
    public void updateEmailSettings(
            Boolean orderEmail, Boolean chatEmail, Boolean reviewEmail,
            Boolean reservationEmail, Boolean stockEmail, Boolean settlementEmail
    ) {
        if (orderEmail       != null) this.orderEmailEnabled       = orderEmail;
        if (chatEmail        != null) this.chatEmailEnabled        = chatEmail;
        if (reviewEmail      != null) this.reviewEmailEnabled      = reviewEmail;
        if (reservationEmail != null) this.reservationEmailEnabled = reservationEmail;
        if (stockEmail       != null) this.stockEmailEnabled       = stockEmail;
        if (settlementEmail  != null) this.settlementEmailEnabled  = settlementEmail;
    }

    // 알림음 설정 일괄 업데이트
    public void updateSoundSettings(
            Boolean orderSound, Boolean chatSound, Boolean reviewSound,
            Boolean reservationSound, Boolean stockSound, Boolean settlementSound
    ) {
        if (orderSound       != null) this.orderSoundEnabled       = orderSound;
        if (chatSound        != null) this.chatSoundEnabled        = chatSound;
        if (reviewSound      != null) this.reviewSoundEnabled      = reviewSound;
        if (reservationSound != null) this.reservationSoundEnabled = reservationSound;
        if (stockSound       != null) this.stockSoundEnabled       = stockSound;
        if (settlementSound  != null) this.settlementSoundEnabled  = settlementSound;
    }
}
