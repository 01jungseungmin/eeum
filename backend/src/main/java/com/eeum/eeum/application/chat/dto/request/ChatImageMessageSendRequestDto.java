package com.eeum.eeum.application.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이미지 메시지 발송 요청 (S3 업로드 완료 후 URL 전달)")
public class ChatImageMessageSendRequestDto {

    @Schema(description = "업로드된 이미지 URL", example = "https://cdn.example.com/chat/abc.jpg")
    @NotBlank(message = "이미지 URL은 필수입니다")
    @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다")
    private String imageUrl;

    @Schema(description = "클라이언트 발급 메시지 UUID — 재전송 시 중복 차단용 (선택)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String clientMessageId;
}
