package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FCM 단건 테스트 발송 결과")
public record AiFcmTestSendResponseDto(
        @Schema(description = "발송 성공 여부", example = "true")
        boolean success,

        @Schema(description = "결과 메시지", example = "테스트 푸시 발송 성공")
        String message
) {
    public static AiFcmTestSendResponseDto ofSuccess() {
        return new AiFcmTestSendResponseDto(true, "테스트 푸시 발송 성공");
    }

    public static AiFcmTestSendResponseDto ofFailure(String errorCode) {
        return new AiFcmTestSendResponseDto(false, "테스트 푸시 발송 실패: " + errorCode);
    }
}
