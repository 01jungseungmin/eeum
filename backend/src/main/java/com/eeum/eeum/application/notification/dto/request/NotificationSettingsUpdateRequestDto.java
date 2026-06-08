package com.eeum.eeum.application.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalTime;

/**
 * 알림 설정 수정 요청.
 * null인 필드는 변경하지 않는다 (부분 업데이트 방식).
 * ORDER / RESERVATION / SYSTEM은 필수이므로 수정 불가 (서버에서 무시).
 */
@Getter
@Schema(description = "알림 수신 설정 수정 요청")
public class NotificationSettingsUpdateRequestDto {

    //푸시 ON/OFF

    @Schema(description = "채팅 알림 ON/OFF", example = "true")
    private Boolean chatEnabled;

    @Schema(description = "커뮤니티 알림 ON/OFF", example = "true")
    private Boolean communityEnabled;

    @Schema(description = "상점/리뷰 알림 ON/OFF", example = "true")
    private Boolean storeReviewEnabled;

    @Schema(description = "중고거래 알림 ON/OFF", example = "false")
    private Boolean usedProductEnabled;

    @Schema(description = "재고 경고 알림 ON/OFF (사장님)", example = "true")
    private Boolean stockEnabled;

    @Schema(description = "정산 완료 알림 ON/OFF (사장님)", example = "true")
    private Boolean settlementEnabled;

    @Schema(description = "마케팅/이벤트 알림 ON/OFF (동의/거부)", example = "false")
    private Boolean marketingEnabled;

    //방해 금지 (DND)

    @Schema(description = "방해 금지 모드 활성화 여부", example = "true")
    private Boolean dndEnabled;

    @Schema(description = "방해 금지 시작 시각 (HH:mm)", example = "22:00",
            type = "string", format = "time")
    private LocalTime dndStartTime;

    @Schema(description = "방해 금지 종료 시각 (HH:mm)", example = "08:00",
            type = "string", format = "time")
    private LocalTime dndEndTime;

    //이메일 수신 설정 (카테고리별)

    @Schema(description = "주문 이메일 수신", example = "true")
    private Boolean orderEmailEnabled;

    @Schema(description = "채팅 이메일 수신", example = "false")
    private Boolean chatEmailEnabled;

    @Schema(description = "리뷰 이메일 수신", example = "true")
    private Boolean reviewEmailEnabled;

    @Schema(description = "예약 이메일 수신", example = "false")
    private Boolean reservationEmailEnabled;

    @Schema(description = "재고 이메일 수신", example = "true")
    private Boolean stockEmailEnabled;

    @Schema(description = "정산 이메일 수신", example = "true")
    private Boolean settlementEmailEnabled;

    //알림음 설정 (카테고리별)

    @Schema(description = "주문 알림음", example = "true")
    private Boolean orderSoundEnabled;

    @Schema(description = "채팅 알림음", example = "true")
    private Boolean chatSoundEnabled;

    @Schema(description = "리뷰 알림음", example = "false")
    private Boolean reviewSoundEnabled;

    @Schema(description = "예약 알림음", example = "true")
    private Boolean reservationSoundEnabled;

    @Schema(description = "재고 알림음", example = "false")
    private Boolean stockSoundEnabled;

    @Schema(description = "정산 알림음", example = "false")
    private Boolean settlementSoundEnabled;
}
