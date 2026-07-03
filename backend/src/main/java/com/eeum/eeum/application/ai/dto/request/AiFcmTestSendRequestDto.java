package com.eeum.eeum.application.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "FCM 단건 테스트 발송 요청 — messageId가 있으면 해당 AI 생성 메시지의 제목/본문을 사용")
public record AiFcmTestSendRequestDto(
        @Schema(description = "AI 생성 메시지 ID (선택 — 있으면 title/content 무시)", example = "1")
        Long messageId,

        @NotBlank(message = "fcmToken은 필수입니다")
        @Schema(description = "수신 기기 FCM 토큰 (발표용 직접 입력)", example = "fcm-device-token")
        String fcmToken,

        @Schema(description = "직접 입력 제목 (messageId 없을 때 사용)")
        String title,

        @Schema(description = "직접 입력 본문 (messageId 없을 때 사용)")
        String content
) {
}
